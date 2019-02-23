package us.ilite.robot.driverinput;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.flybotix.hfr.codex.Codex;
import com.flybotix.hfr.util.log.ILog;
import com.flybotix.hfr.util.log.Logger;
import com.team254.lib.util.Util;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import us.ilite.common.Data;
import us.ilite.common.config.DriveTeamInputMap;
import us.ilite.common.config.SystemSettings;
import us.ilite.common.lib.util.RangeScale;
import us.ilite.common.types.ETrackingType;
import us.ilite.common.types.input.EInputScale;
import us.ilite.common.types.input.ELogitech310;
import us.ilite.robot.modules.Drive;
import us.ilite.robot.modules.DriveMessage;
import us.ilite.robot.modules.HatchFlower;
import us.ilite.robot.commands.TargetLock;
import us.ilite.robot.modules.*;
import us.ilite.robot.modules.Module;
import us.ilite.robot.modules.Intake.EIntakeState;
import us.ilite.robot.commands.HandoffHatch;

public class DriverInput extends Module implements IThrottleProvider, ITurnProvider {

    protected static final double 
    DRIVER_SUB_WARP_AXIS_THRESHOLD = 0.5;
    private ILog mLog = Logger.createLog(DriverInput.class);


    protected final Drive mDrive;
    protected final Elevator mElevator;
    protected final Intake mIntake;
    protected final CargoSpit mCargoSpit;
    protected final HatchFlower mHatchFlower;
    private final CommandManager mTeleopCommandManager;
    private final CommandManager mAutonomousCommandManager;
    private final Limelight mLimelight;
    private final Data mData;

    private boolean mIsCargo = false;
    private boolean mIsGround = false;
    private Joystick mDriverJoystick;
    private Joystick mOperatorJoystick;

    private Arm mArm;
    private RangeScale armJoyStickToAngleScaler = new RangeScale(-1.0, 1.0, SystemSettings.kArmMinAngle, SystemSettings.kArmMaxAngle);

    protected Codex<Double, ELogitech310> mDriverInputCodex, mOperatorInputCodex;

    private ETrackingType mLastTrackingType = null;

    public DriverInput(Drive pDrivetrain, Elevator pElevator, HatchFlower pHatchFlower, Intake pIntake, CargoSpit pCargoSpit, Arm pArm, Limelight pLimelight, Data pData, CommandManager pTeleopCommandManager, CommandManager pAutonomousCommandManager, boolean pSimulated) {
        this.mDrive = pDrivetrain;
        this.mElevator = pElevator;
        this.mIntake = pIntake;
        this.mCargoSpit = pCargoSpit;
        this.mHatchFlower = pHatchFlower;
        this.mArm = pArm;
        this.mLimelight = pLimelight;
        this.mData = pData;
        this.mTeleopCommandManager = pTeleopCommandManager;
        this.mAutonomousCommandManager = pAutonomousCommandManager;

        this.mDriverInputCodex = mData.driverinput;
        this.mOperatorInputCodex = mData.operatorinput;
        if(pSimulated) {
            // Use a different joystick library?
            
        } else {
            this.mDriverJoystick = new Joystick(0);
            this.mOperatorJoystick = new Joystick(1);
        }
    }

    public DriverInput(Drive pDrivetrain, Elevator pElevator, HatchFlower pHatchFlower, Intake pIntake, CargoSpit pCargoSpit, Arm pArm, Limelight pLimelight, Data pData, CommandManager pTeleopCommandManager, CommandManager pAutonomousCommandManager) {
        this(pDrivetrain, pElevator, pHatchFlower, pIntake, pCargoSpit, pArm, pLimelight, pData, pTeleopCommandManager, pAutonomousCommandManager, false);
    }

    @Override
    public void modeInit(double pNow) {

    }

    @Override
    public void periodicInput(double pNow) {
        ELogitech310.map(mData.driverinput, mDriverJoystick);
        ELogitech310.map(mData.operatorinput, mOperatorJoystick);
    }

    @Override
    public void update(double pNow) {
        /*
        If we aren't already running commands and the driver is pressing a button that triggers a command,
        set the superstructure command queue based off of buttons
        */
        if(isDriverAllowingAutonomousControlInTeleop()) {
            updateVisionCommands();
        /*
        If the driver started the commands that the superstructure is running and then released the button,
        stop running commands.
        */
        } else if(mAutonomousCommandManager.isRunningCommands() && !isDriverAllowingAutonomousControlInTeleop()) {
            mLog.warn("Requesting command stop: driver no longer allowing commands");
            mAutonomousCommandManager.stopRunningCommands();
        }

        if(mAutonomousCommandManager.isRunningCommands() && isAutoOverridePressed()) {
            mLog.warn("Requesting command stop: override pressed");
            mAutonomousCommandManager.stopRunningCommands();
        }

        // Teleop control
        if (!mAutonomousCommandManager.isRunningCommands()) {

            if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_CARGO_SELECT)) {
                mIsCargo = true;
            } else if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_HATCH_SELECT)) {
                mIsCargo = false;
            }

            if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_GROUND_SELECT)) {
                mIsGround = true;
            } else if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_LOADING_STATION_SELECT)){
                mIsGround = false;
            }

            updateDriveTrain();
            updateHatchGrabber();
            updateElevator();
            updateIntake();

        }

    }

    private void updateIntake() {
        if(mOperatorInputCodex.get(DriveTeamInputMap.OPERATOR_ACQUIRE) > 0.5) {
            if(mIsCargo) {
                /*
                Tell both the intake and the cargo spit to start intaking.
                We expect the cargo spit to stop automatically.
                 */
                if(mIsGround) {
                    mIntake.setIntakeState( EIntakeState.GROUND_CARGO );
                    mCargoSpit.setIntake();
                }
            } else {
                /*
                Reset the hatch grabber so it's ready to receive another hatch and tell the intake to start intaking.
                We intake to stop automatically, or when we release the intake button.
                 */
                if(mIsGround) {
                    mHatchFlower.pushHatch();
                    mIntake.setIntakeState( EIntakeState.GROUND_HATCH );
                } else {
                    mHatchFlower.captureHatch();
                }
            }
        } else if(mOperatorInputCodex.get(DriveTeamInputMap.OPERATOR_SCORE) > 0.5) {
            // if(mIntake.isAtPosition( Intake.EWristState.STOWED) || mIntake.isAtPosition(Intake.EWristState.HANDOFF)) {
                if(mIsCargo) {
                    mCargoSpit.setOuttake();
                } else {
                    mHatchFlower.pushHatch();
                }
            // } else {
                // If the intake is on the ground, outtake with the intake instead of scoring mechanisms
//                mIntake.setOuttaking();
            //     mIntake.setIntakeState( EIntakeState.HANDOFF ); //TODO this probably isn't right
            // }
        } else {
            // If the intake button is released, stop everything.
            mCargoSpit.stop();
            mIntake.stopIntake();
        }

        if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_HANDOFF) /* || mIntake.hasHatch() */) { //TODO Subject to change
            mTeleopCommandManager.startCommands(new HandoffHatch(mElevator, mIntake, mHatchFlower));
        }

    }

    private void updateHatchGrabber() {

        if(mIsCargo) {
            mHatchFlower.setFlowerExtended(HatchFlower.ExtensionState.UP);
        } else {
            mHatchFlower.setFlowerExtended(HatchFlower.ExtensionState.DOWN);
        }

    }

    private void updateDriveTrain() {
        double rotate = getTurn();
        double throttle = getThrottle();

        //		    throttle = EInputScale.EXPONENTIAL.map(throttle, 2);
        rotate = EInputScale.EXPONENTIAL.map(rotate, 2);
        rotate = Util.limit(rotate, 0.7);

        if (mData.driverinput.isSet(DriveTeamInputMap.DRIVER_SUB_WARP_AXIS) && mData.driverinput.get(DriveTeamInputMap.DRIVER_SUB_WARP_AXIS) > DRIVER_SUB_WARP_AXIS_THRESHOLD) {
            throttle *= SystemSettings.kSnailModePercentThrottleReduction;
            rotate *= SystemSettings.kSnailModePercentRotateReduction;
        }

        DriveMessage driveMessage = DriveMessage.fromThrottleAndTurn(throttle, rotate);
        driveMessage.setNeutralMode(NeutralMode.Brake);
        driveMessage.setControlMode(ControlMode.PercentOutput);

        mDrive.setDriveMessage(driveMessage);
    }

    private void updateElevator() {

        if(mIsCargo) {
            if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_BOTTOM_POSITION_ELEVATOR)) {
                mElevator.setDesiredPosition(EElevatorPosition.CARGO_BOTTOM);
            } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_MIDDLE_POSITION_ELEVATOR)) {
                mElevator.setDesiredPosition(EElevatorPosition.CARGO_MIDDLE);
            } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_TOP_POSITION_ELEVATOR)) {
                mElevator.setDesiredPosition(EElevatorPosition.CARGO_TOP);
            } else if (mData.driverinput.isSet(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR)) {
                double throttle = -mData.operatorinput.get(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR);
                mElevator.setDesiredPower(throttle);
            } else {
                mElevator.setDesiredPower(0d);
            }
        } else {
            if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_BOTTOM_POSITION_ELEVATOR)) {
                mElevator.setDesiredPosition(EElevatorPosition.HATCH_BOTTOM);
            } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_MIDDLE_POSITION_ELEVATOR)) {
                mElevator.setDesiredPosition(EElevatorPosition.HATCH_MIDDLE);
            } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_TOP_POSITION_ELEVATOR)) {
                mElevator.setDesiredPosition(EElevatorPosition.HATCH_TOP);
            } else if (mData.driverinput.isSet(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR)) {
                double throttle = -mData.operatorinput.get(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR);
                mElevator.setDesiredPower(throttle);
            } else {
                mElevator.setDesiredPower(0d);
            }
        }

        if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_GROUND_POSITION_ELEVATOR)) {
            mElevator.setDesiredPosition(EElevatorPosition.HATCH_BOTTOM);
        }

         
    }
      
    private void updateSplitTriggerAxisFlip() {

        double rotate = getTurn();
        double throttle = getThrottle();

        if (mDriverInputCodex.get(ELogitech310.RIGHT_TRIGGER_AXIS) > 0.3) {
            rotate = rotate;
            throttle = throttle;
        } else if (mDriverInputCodex.get(ELogitech310.LEFT_TRIGGER_AXIS) > 0.3) {
            throttle = -throttle;
            rotate = rotate;
        }

        rotate = Util.limit(rotate, SystemSettings.kDriverInputTurnMaxMagnitude);

        // throttle = EInputScale.EXPONENTIAL.map(throttle, 2);
        // rotate = Util.limit(rotate, 0.7);

        // if (mDriverInputCodex.get(DriveTeamInputMap.DRIVER_SUB_WARP_AXIS) > DRIVER_SUB_WARP_AXIS_THRESHOLD) {
        //     throttle *= SystemSettings.kSnailModePercentThrottleReduction;
        //     rotate *= SystemSettings.kSnailModePercentRotateReduction;
        // }

        DriveMessage driveMessage = DriveMessage.fromThrottleAndTurn(throttle, rotate);

        driveMessage.setNeutralMode(NeutralMode.Brake);
        driveMessage.setControlMode(ControlMode.PercentOutput);

        mDrive.setDriveMessage(driveMessage);

    }

    /**
     * Commands the superstructure to start vision tracking depending on
     * button presses.
     */
    protected void updateVisionCommands() {

        ETrackingType trackingType = null;
        SystemSettings.VisionTarget visionTarget = null;

        // Switch the limelight to a pipeline and track
        if(mDriverInputCodex.isSet(DriveTeamInputMap.DRIVER_TRACK_TARGET_BTN)) {
            trackingType = ETrackingType.TARGET_LEFT;
            // TODO Determine which target height we're using
            visionTarget = SystemSettings.VisionTarget.HatchPort;
        } else if(mDriverInputCodex.isSet(DriveTeamInputMap.DRIVER_TRACK_CARGO_BTN)) {
            trackingType = ETrackingType.CARGO_LEFT;
            visionTarget = SystemSettings.VisionTarget.CargoHeight;
        } else if(mDriverInputCodex.isSet(DriveTeamInputMap.DRIVER_TRACK_HATCH_BTN)) {
            trackingType = ETrackingType.LINE_LEFT;
            visionTarget = SystemSettings.VisionTarget.Ground;
        }

        // If the driver selected a tracking enum and we won't go out of bounds
        if(trackingType != null && trackingType.ordinal() < ETrackingType.values().length - 1) {
            int trackingTypeOrdinal = trackingType.ordinal();
            if (mDriverInputCodex.isSet(DriveTeamInputMap.DRIVER_NUDGE_SEEK_LEFT)) {
                // If driver wants to seek left, we don't need to change anything
                trackingType = ETrackingType.values()[trackingTypeOrdinal];
            } else if (mDriverInputCodex.isSet(DriveTeamInputMap.DRIVER_NUDGE_SEEK_RIGHT)) {
                // If driver wants to seek right, switch from "_LEFT" enum to "_RIGHT" enum
                trackingType = ETrackingType.values()[trackingTypeOrdinal + 1];
            } else {
                trackingType = null;
            }
        }

        if(trackingType != null && trackingType != mLastTrackingType) {
            mLimelight.setVisionTarget(visionTarget);
            mLimelight.setPipeline(trackingType.getPipeline());
            mLog.warn("Requesting command start");
            mAutonomousCommandManager.stopRunningCommands();
            mAutonomousCommandManager.startCommands(new TargetLock(mDrive, 3, trackingType, mLimelight, this, false));
            SmartDashboard.putString("Last Tracking Type", mLastTrackingType == null ? "Null" : mLastTrackingType.name());
            SmartDashboard.putString("Tracking Type", trackingType.name());
        }

        mLastTrackingType = trackingType;
    }

    public boolean isDriverAllowingAutonomousControlInTeleop() {
        boolean runCommands = false;
        for(ELogitech310 l : SystemSettings.kTeleopCommandTriggers) {
            if(mDriverInputCodex.isSet(l)) {
                runCommands = true;
            }
        }
        return runCommands;
    }

    public boolean isAutoOverridePressed() {
        boolean autonOverride = false;
        for(ELogitech310 l : SystemSettings.kAutonOverrideTriggers) {
            if(mDriverInputCodex.isSet(l) && mDriverInputCodex.get(l) > SystemSettings.kAutonOverrideAxisThreshold) {
                autonOverride = true;
            }
        }
        return autonOverride;
    }

    @Override
    public void shutdown(double pNow) {

    }

    @Override
    public double getThrottle() {
        if(mData.driverinput.isSet(DriveTeamInputMap.DRIVER_THROTTLE_AXIS)) {
            return -mData.driverinput.get(DriveTeamInputMap.DRIVER_THROTTLE_AXIS);
        } else {
            return 0.0;
        }
    }

    @Override
    public double getTurn() {
        if(mData.driverinput.isSet(DriveTeamInputMap.DRIVER_TURN_AXIS)) {
            return mData.driverinput.get(DriveTeamInputMap.DRIVER_TURN_AXIS);
        } else {
            return 0.0;
        }
    }
}

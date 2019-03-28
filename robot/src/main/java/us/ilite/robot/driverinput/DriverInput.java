package us.ilite.robot.driverinput;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.flybotix.hfr.codex.Codex;
import com.flybotix.hfr.util.log.ILog;
import com.flybotix.hfr.util.log.Logger;
import us.ilite.common.lib.util.CheesyDriveHelper;
import com.team254.lib.util.DriveSignal;
import com.team254.lib.util.Util;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import us.ilite.common.Data;
import us.ilite.common.config.DriveTeamInputMap;
import us.ilite.common.config.SystemSettings;
import us.ilite.common.types.ETrackingType;
import us.ilite.common.types.input.EInputScale;
import us.ilite.common.types.input.ELogitech310;
import us.ilite.lib.drivers.ECommonControlMode;
import us.ilite.lib.drivers.ECommonNeutralMode;
import us.ilite.robot.commands.LimelightTargetLock;
import us.ilite.robot.modules.Drive;
import us.ilite.robot.modules.DriveMessage;
import us.ilite.robot.modules.HatchFlower;
import us.ilite.robot.modules.*;
import us.ilite.robot.modules.Module;
import us.ilite.robot.modules.Intake.EIntakeState;

public class DriverInput extends Module implements IThrottleProvider, ITurnProvider {

    protected static final double 
    DRIVER_SUB_WARP_AXIS_THRESHOLD = 0.5;
    private ILog mLog = Logger.createLog(DriverInput.class);


    protected final Drive mDrive;
    protected final Elevator mElevator;
    protected final Intake mIntake;
    protected final PneumaticIntake mPneumaticIntake;
    protected final CargoSpit mCargoSpit;
    protected final HatchFlower mHatchFlower;
    protected final FourBar mFourBar;
    private final CommandManager mTeleopCommandManager;
    private final CommandManager mAutonomousCommandManager;
    private final Limelight mLimelight;
    private final Data mData;
    private Timer mGroundCargoTimer = new Timer();

    private boolean mIsCargo = false;
    private Joystick mDriverJoystick;
    private Joystick mOperatorJoystick;

    private CheesyDriveHelper mCheesyDriveHelper = new CheesyDriveHelper(SystemSettings.kCheesyDriveGains);

    protected Codex<Double, ELogitech310> mDriverInputCodex, mOperatorInputCodex;

    private ETrackingType mLastTrackingType = null;

    public DriverInput(Drive pDrivetrain, Elevator pElevator, HatchFlower pHatchFlower, Intake pIntake, PneumaticIntake pPneumaticIntake, CargoSpit pCargoSpit, Limelight pLimelight, Data pData, CommandManager pTeleopCommandManager, CommandManager pAutonomousCommandManager, FourBar pFourBar, boolean pSimulated) {
        this.mDrive = pDrivetrain;
        this.mElevator = pElevator;
        this.mIntake = pIntake;
        this.mPneumaticIntake = pPneumaticIntake;
        this.mCargoSpit = pCargoSpit;
        this.mHatchFlower = pHatchFlower;
        this.mLimelight = pLimelight;
        this.mData = pData;
        this.mTeleopCommandManager = pTeleopCommandManager;
        this.mAutonomousCommandManager = pAutonomousCommandManager;
        this.mFourBar = pFourBar;

        this.mDriverInputCodex = mData.driverinput;
        this.mOperatorInputCodex = mData.operatorinput;
        if(pSimulated) {
            // Use a different joystick library?
            
        } else {
            this.mDriverJoystick = new Joystick(0);
            this.mOperatorJoystick = new Joystick(1);
        }
    }

    public DriverInput(Drive pDrivetrain, Elevator pElevator, HatchFlower pHatchFlower, Intake pIntake, PneumaticIntake pPneumaticIntake, CargoSpit pCargoSpit, Limelight pLimelight, Data pData, CommandManager pTeleopCommandManager, CommandManager pAutonomousCommandManager, FourBar pFourBar) {
        this(pDrivetrain, pElevator, pHatchFlower, pIntake, pPneumaticIntake, pCargoSpit, pLimelight, pData, pTeleopCommandManager, pAutonomousCommandManager, pFourBar, false);
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
        if(isDriverAllowingCommandsInTeleop()) {
            mAutonomousCommandManager.stopRunningCommands(pNow);
            updateVisionCommands(pNow);
        /*
        If the driver started the commands that the superstructure is running and then released the button,
        stop running commands.
        */
        } else if(mTeleopCommandManager.isRunningCommands() && !isDriverAllowingCommandsInTeleop()) {
            mLog.warn("Requesting command stop: driver no longer allowing commands");
            mTeleopCommandManager.stopRunningCommands(pNow);
        }

        if(mAutonomousCommandManager.isRunningCommands() && isAutoOverridePressed()) {
            mLog.warn("Requesting command stop: override pressed");
            mAutonomousCommandManager.stopRunningCommands(pNow);
        }

        // Teleop control
        if (!mAutonomousCommandManager.isRunningCommands()) {

            if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_CARGO_SELECT)) {
                mIsCargo = true;
            } else if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_HATCH_SELECT)) {
                mIsCargo = false;
            }

            updateDriveTrain();
            updateFourBar();
//            updateCheesyDrivetrain();
            updateHatchGrabber();
            updateCargoSpit();
            updateElevator();
//            updateIntake();
            updatePneumaticIntake();
        }

    }

    private void updateIntake() {

        // Default to retracted
        EGamePiece gamePiece = EGamePiece.HATCH;

        if (mOperatorInputCodex.get(DriveTeamInputMap.OPERATOR_INTAKE_GROUND) > 0.5) {
            mIntake.setIntakeState( EIntakeState.GROUND );
            System.out.println("Setting intake to GROUND");
            gamePiece = mIsCargo ? EGamePiece.CARGO : EGamePiece.HATCH;
        }
        else if (mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_INTAKE_STOWED)) {
            mIntake.setIntakeState( EIntakeState.STOWED );
            System.out.println("Setting intake to STOWED");
        } 
        else if (mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_INTAKE_HANDOFF)) {
            mIntake.setIntakeState( EIntakeState.HANDOFF );
        } else if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_MANUAL_ARM_UP)) {
            mIntake.overridePower(0.3);
        } else if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_MANUAL_ARM_DOWN)) {
            mIntake.overridePower(-0.3);
        } else {
            mIntake.setIntakeState(EIntakeState.STOPPED);
        }

        // if(mOperatorInputCodex.isSet(DriveTeamInputMap.WRIST_MANUAL_POWER)) {
        //     mIntake.overridePower(mOperatorInputCodex.get(DriveTeamInputMap.WRIST_MANUAL_POWER));
        // }


        mIntake.setGamePiece(gamePiece);

    }

    private void updateHatchGrabber() {

        if (mIsCargo) {
            // Hatch grabber up so we can recieve cargo
            mHatchFlower.setFlowerExtended(HatchFlower.ExtensionState.UP);
            // Keep hatch grabber open so we can use cargo mode to bring hatch grabber up
//            mHatchFlower.pushHatch();
        } else {
            // Hatch grabber down
            mHatchFlower.setFlowerExtended(HatchFlower.ExtensionState.DOWN);

            if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_INTAKE_LOADING_STATION)) {
                // Intake from loading station - grab hatch
                mHatchFlower.captureHatch();
            } else if (mData.operatorinput.get(DriveTeamInputMap.OPERATOR_INTAKE_GROUND) > 0.5) {
                // Grabbing is handled automagically
            } else if (mData.operatorinput.get(DriveTeamInputMap.OPERATOR_SCORE) > 0.5) {
                // Score - release hatch
                mHatchFlower.pushHatch();
            } else {
                // Do nothing
            }
        }

    }

    private void updatePneumaticIntake() {
        if(mIsCargo) {
            if ( mData.operatorinput.get( DriveTeamInputMap.OPERATOR_INTAKE_GROUND ) > 0.5 ) {
                mPneumaticIntake.setDesiredPosition( PneumaticIntake.EPneumaticIntakePosition.OUT );
            } else {
                mPneumaticIntake.setDesiredPosition( PneumaticIntake.EPneumaticIntakePosition.STOWED );
            }
        } else {
            mPneumaticIntake.setDesiredPosition(PneumaticIntake.EPneumaticIntakePosition.STOWED);
        }
    }

    private void updateFourBar() {
        if (mData.driverinput.isSet(DriveTeamInputMap.DRIVER_CLIMBER_ALLOW)) {

            if(Math.abs(mData.operatorinput.get(DriveTeamInputMap.OPERATOR_CLIMBER_AXIS)) > 0.02) {
                mFourBar.setDesiredOutput(mData.operatorinput.get(DriveTeamInputMap.OPERATOR_CLIMBER_AXIS) * 0.7, false);
            }

            if(mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_PUSHER_BUTTON)) {
                mFourBar.extendPusher();
            } else {
                mFourBar.retractPusher();
            }

        } else {
            mFourBar.handleStopType();
            mFourBar.retractPusher();
        }
    }

    private void updateCargoSpit() {
        if(mIsCargo) {
            if(mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_INTAKE_LOADING_STATION)) {
                // Intake from loading station
                mCargoSpit.setIntaking();

                resetCargoTimer();

            } else if(mData.operatorinput.get(DriveTeamInputMap.OPERATOR_INTAKE_GROUND) > 0.5) {
                // Intake from ground - recieve cargo from ground intake
                mCargoSpit.setIntaking();

                mGroundCargoTimer.start();

            } else if(mData.operatorinput.get(DriveTeamInputMap.OPERATOR_SCORE) > 0.5) {
                // Spit
                mCargoSpit.setOuttaking();

                resetCargoTimer();

                // Stop if timer has expired or was reset
            } else if ( mGroundCargoTimer.hasPeriodPassed( SystemSettings.kCargoSpitDelay ) || mGroundCargoTimer.get() == 0 ) {

                // Stop
                resetCargoTimer();

                mCargoSpit.stop();
            }
        }
    }

    private void resetCargoTimer() {
        mGroundCargoTimer.stop();
        mGroundCargoTimer.reset();
    }

    private void updateDriveTrain() {
        double rotate = getTurn();
        double throttle = getThrottle();

        //		    throttle = EInputScale.EXPONENTIAL.map(throttle, 2);
        rotate = EInputScale.EXPONENTIAL.map(rotate, 2);
        rotate *= SystemSettings.kNormalPercentThrottleReduction;

        if (mData.driverinput.isSet(DriveTeamInputMap.DRIVER_SUB_WARP_AXIS) && mData.driverinput.get(DriveTeamInputMap.DRIVER_SUB_WARP_AXIS) > DRIVER_SUB_WARP_AXIS_THRESHOLD) {
            throttle *= SystemSettings.kSnailModePercentThrottleReduction;
            rotate *= SystemSettings.kSnailModePercentRotateReduction;
        }

        DriveMessage driveMessage = DriveMessage.fromThrottleAndTurn(throttle, rotate);
        driveMessage.setNeutralMode(ECommonNeutralMode.BRAKE);
        driveMessage.setControlMode(ECommonControlMode.PERCENT_OUTPUT);

        mDrive.setDriveMessage(driveMessage);
    }

    private void updateCheesyDrivetrain() {
        boolean isQuickTurn = mData.driverinput.get(ELogitech310.RIGHT_TRIGGER_AXIS) > 0.5;
        DriveSignal cheesySignal = mCheesyDriveHelper.cheesyDrive(getThrottle(), getTurn() * 0.5, isQuickTurn);
        DriveMessage driveMessage = new DriveMessage(cheesySignal.getLeft(), cheesySignal.getRight(), ECommonControlMode.PERCENT_OUTPUT);
        mDrive.setDriveMessage(driveMessage);
    }

    private void updateElevator() {

        double manualThrottle = -mData.operatorinput.get(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR) * 0.5;


        if(mOperatorInputCodex.isSet(DriveTeamInputMap.OPERATOR_GROUND_POSITION_ELEVATOR)) {
            mElevator.setDesiredPosition(Elevator.EElevatorPosition.HATCH_BOTTOM);
        } else {
            if(mIsCargo) {
                if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_BOTTOM_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.CARGO_BOTTOM);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_LOW_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.CARGO_CARGO_SHIP);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_MIDDLE_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.CARGO_MIDDLE);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_TOP_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.CARGO_TOP);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_INTAKE_LOADING_STATION)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.CARGO_LOADING_STATION);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR)) {
                    mElevator.setDesiredPower(manualThrottle);
                } else {
                    mElevator.setDesiredPower(0d);
                }
            } else {
                if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_BOTTOM_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.HATCH_BOTTOM);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_LOW_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.HATCH_MIDDLE);
                } else if (mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_TOP_POSITION_ELEVATOR) ||
                            mData.operatorinput.isSet(DriveTeamInputMap.OPERATOR_MIDDLE_POSITION_ELEVATOR)) {
                    mElevator.setDesiredPosition(Elevator.EElevatorPosition.HATCH_TOP);
                } else if (mData.driverinput.isSet(DriveTeamInputMap.OPERATOR_CONTROL_ELEVATOR)) {
                    mElevator.setDesiredPower(manualThrottle);
                } else {
                    mElevator.setDesiredPower(0d);
                }
            }
    
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

        // throttle = EInputScale.EXPONENTIAL.map(throttle, 2);
        // rotate = Util.limit(rotate, 0.7);

        // if (mDriverInputCodex.get(DriveTeamInputMap.DRIVER_SUB_WARP_AXIS) > DRIVER_SUB_WARP_AXIS_THRESHOLD) {
        //     throttle *= SystemSettings.kSnailModePercentThrottleReduction;
        //     rotate *= SystemSettings.kSnailModePercentRotateReduction;
        // }
        rotate = Util.limit(rotate, SystemSettings.kDriverInputTurnMaxMagnitude);
		
        DriveMessage driveMessage = DriveMessage.fromThrottleAndTurn(throttle, rotate);

        driveMessage.setNeutralMode(ECommonNeutralMode.BRAKE);
        driveMessage.setControlMode(ECommonControlMode.PERCENT_OUTPUT);

        mDrive.setDriveMessage(driveMessage);

    }

    /**
     * Commands the superstructure to start vision tracking depending on
     * button presses.
     */
    protected void updateVisionCommands(double pNow) {

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
            mLog.warn(
                    "Requesting command start");
            mTeleopCommandManager.stopRunningCommands(pNow);
            mTeleopCommandManager.startCommands(new LimelightTargetLock(mDrive, mLimelight, 3, trackingType, this, false));
            SmartDashboard.putString("Last Tracking Type", mLastTrackingType == null ? "Null" : mLastTrackingType.name());
            SmartDashboard.putString("Tracking Type", trackingType.name());
        }

        mLastTrackingType = trackingType;
    }

    public boolean isDriverAllowingCommandsInTeleop() {
        boolean runCommands = false;
        for(ELogitech310 l : SystemSettings.kTeleopCommandTriggers) {
            if(mDriverInputCodex.isSet(l) && mDriverInputCodex.get(l) != 0.0) {
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

    public enum EGamePiece {
        CARGO,
        HATCH;
    }
}

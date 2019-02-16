package us.ilite.robot.modules;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;
import com.flybotix.hfr.util.log.ILog;
import com.flybotix.hfr.util.log.Logger;
import com.team254.lib.drivers.talon.TalonSRXFactory;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import us.ilite.common.Data;
import us.ilite.common.config.SystemSettings;
import us.ilite.common.types.drive.EDriveData;
import us.ilite.common.types.manipulator.EIntake;
import us.ilite.common.types.sensor.EPowerDistPanel;


public class Intake extends Module {

    // TODO update allowed error angle
    private final double kZero = 0.0;
    private final String kDefaultError = "This message should not be displayed";
    private final double kTrueBoolean = 1.0;
    private final double kFalseBoolean = 0.0;

    // Logging fields
    private ILog mLog = Logger.createLog(Intake.class);
    private Data mData;

    // Roller fields
    private TalonSRX mIntakeRoller; //14
    private boolean mHasHatch;
    private double mIntakeRollerCurrent;
    private double mIntakeRollerVoltage;

    // Wrist/Arm fields
    private Arm mWrist;
    private Double mWristAngle;

    // Pneumatic/Solenoid for hatch/cargo roller mode toggle
    private Solenoid mSolenoid;

    // Hatch beam break sensor
    // private DigitalInput mHatchBeam;

    // Present intake states
    private ERollerState mRollerState;
    private EWristState mWristPosition;
    private ESolenoidState mSolenoidState;
    private EIntakeState mDesiredIntakeState;

    /**
     * Wrist position based on angle
     */
    public enum EWristState {
        // TODO update wrist position angles
        GROUND(90.0), HANDOFF(45.0), STOWED(0.0);

        public final double kWristAngleDegrees;

        EWristState(double pWristAngle) {
            kWristAngleDegrees = pWristAngle;
        }

    }
    /**
     * Pneumatic/Solenoid state for hatch or cargo roller mode
     */
    public enum ESolenoidState {
        HATCH(false), CARGO(true);

        public final boolean kExtended;

        private ESolenoidState(boolean pExtended) {
            this.kExtended = pExtended;
        }

    }

    /**
     * Roller state
     */
    public enum ERollerState {
        STOPPED(0.0),
        HATCH(SystemSettings.kIntakeRollerHatchPower),
        CARGO(SystemSettings.kIntakeRollerCargoPower),
        HOLD(SystemSettings.kIntakeRollerHoldPower);

        public final double kPower;

        private ERollerState(double pPower) {
            this.kPower = pPower;
        }
    }

    public enum EIntakeState {
        GROUND_HATCH, GROUND_CARGO, HANDOFF, STOWED;
    }

    public Intake(Data pData) {
        // Basic Construction
        // mIntakeRoller = new VictorSPX(SystemSettings.kHatchIntakeSPXAddress);
        // TODO change back to victorspx
        mIntakeRoller = new TalonSRX(7);
        this.mData = pData;
    
        // Wrist control
        // TalonSRX tempTalonSRX = TalonSRXFactory.createDefaultTalon(SystemSettings.kIntakeWristSRXAddress); //11
        TalonSRX tempTalonSRX = TalonSRXFactory.createDefaultTalon(6);
        mWrist = new MotionMagicArm(tempTalonSRX);

        // Solenoid for changing between cargo and hatch mode
        // mSolenoid = new Solenoid(SystemSettings.kIntakeSolenoidAddress);
        mSolenoid = new Solenoid(3);

        mSolenoidState = ESolenoidState.HATCH;
        mRollerState = ERollerState.STOPPED;
        mWristPosition = EWristState.STOWED;

        mDesiredIntakeState = EIntakeState.STOWED;

        // Sensor checking if hatch is in intake
        // mHatchBeam = new DigitalInput(SystemSettings.kIntakeBeamBreakAddress);
    }


    @Override
    public void modeInit(double pNow) {
        mLog.error("MODE INIT");
        mSolenoidState = ESolenoidState.HATCH;
        mRollerState = ERollerState.STOPPED;
        mWristPosition = EWristState.STOWED;
        mDesiredIntakeState = EIntakeState.STOWED;

        mWrist.modeInit(pNow);
    }

    @Override
    public void periodicInput(double pNow) {
        mData.intake.set(EIntake.ARM_ANGLE, mWristAngle);
        mData.intake.set(EIntake.ROLLER_CURRENT, mIntakeRollerCurrent);
        mData.intake.set(EIntake.ROLLER_VOLTAGE, mIntakeRollerVoltage);
        mData.intake.set(EIntake.SOLENOID_EXTENDED, mSolenoid.get() ? kTrueBoolean : kFalseBoolean);
        
        mWrist.periodicInput(pNow);

    }

    @Override
    public void update(double pNow) {
        mWrist.update(pNow);

        // EPowerDistPanel ID 12 (CURRENT12) corresponds to Intake Rollers
        // mIntakeRollerCurrent = mData.pdp.get(EPowerDistPanel.CURRENT12);
        mIntakeRollerVoltage = mIntakeRoller.getMotorOutputVoltage();
        // if (mIntakeRollerCurrent > SystemSettings.kIntakeRollerCurrentLimit) {
        //     stopRoller();
        //     mHasHatch = true;
        // }

        mWristAngle = mWrist.getCurrentArmAngle();
        
        // SmartDashboard.putString("Desired State", mDesiredIntakeState.name());        
        // SmartDashboard.putNumber("pNow var", pNow);
        // SmartDashboard.putNumber("Intake Wrist Angle", mWristAngle);

        switch (mDesiredIntakeState) {
            case GROUND_HATCH:
                setWristState(EWristState.GROUND);
                if (mWristAngle < SystemSettings.kIntakeWristBound) break;
                setSolenoidState(ESolenoidState.HATCH);
                setRollerState(ERollerState.HATCH);
                break;
            case GROUND_CARGO:
                setWristState(EWristState.GROUND);
                if (mWristAngle < SystemSettings.kIntakeWristBound) break;
                setSolenoidState(ESolenoidState.CARGO);
                setRollerState(ERollerState.CARGO);
                break;
            case HANDOFF:
                setSolenoidState(ESolenoidState.HATCH);
                setRollerState(ERollerState.HOLD);
                setWristState(EWristState.HANDOFF);
                break;
            case STOWED:
                setSolenoidState(ESolenoidState.HATCH);
                setRollerState(ERollerState.STOPPED);
                setWristState(EWristState.STOWED);
                break;
            default:
                mLog.error(kDefaultError);
                break;
        }
    }

    @Override
    public void shutdown(double pNow) {
        mWrist.shutdown(pNow);
    }

    @Override
    public boolean checkModule(double pNow) {
        mWrist.checkModule(pNow);
        return false;
    }


    /**
     * Sets the wrist position to a preset position.
     * @param pWristPosition desired wrist position
     */
    public void setWristState(EWristState pWristPosition) {
        switch(pWristPosition) {
            case GROUND:
                mWrist.setArmAngle(pWristPosition.kWristAngleDegrees);
                mWristPosition = pWristPosition;
                break;
            case HANDOFF:
                mWrist.setArmAngle(pWristPosition.kWristAngleDegrees);
                mWristPosition = pWristPosition;
                break;
            case STOWED:
                mWrist.setArmAngle(pWristPosition.kWristAngleDegrees);
                mWristPosition = pWristPosition;
                break;
            default:
                mLog.error(kDefaultError);
                break;
        }
    }

    /**
     * Main method for controlling the rollers.
     * Roller power is based on current solenoid state (HATCH/CARGO).
     */
    public void setRollerState(ERollerState pRollerState) {
        switch(pRollerState) {
            case STOPPED:
                setRollerPower(pRollerState.kPower);
                mRollerState = pRollerState;
                break;
            case HATCH:
                setRollerPower(pRollerState.kPower);
                mRollerState = pRollerState;
                break;
            case CARGO:
                setRollerPower(pRollerState.kPower);
                mRollerState = pRollerState;
                break;
            case HOLD:
                setRollerPower(pRollerState.kPower);
                mRollerState = pRollerState;
                break;
            default:
                mLog.error(kDefaultError);
                break;
        }
    }
    // TODO Power depends on gamepiece? On robot speed? On both?
    /**
     * Starts the intake roller.
     * @param pPower percent of power to use
     */
    public void setRollerPower(double pPower) {
        // Gets average speed: (left velocity + right velocity) / 2
        // Double speed = ( mData.drive.get(EDriveData.LEFT_VEL_IPS) + mData.drive.get(EDriveData.RIGHT_VEL_IPS) ) / 2;

        mIntakeRoller.set(ControlMode.PercentOutput, pPower);
    }

    /**
     * Sets the solenoid state.
     * i.e. sets whether the intake is in "cargo mode" or "hatch mode".
     * @param pSolenoidState desired solenoid state
     */
    public void setSolenoidState(ESolenoidState pSolenoidState) {
        mSolenoid.set(pSolenoidState.kExtended);
        mSolenoidState = pSolenoidState;
    }

    public void stopRoller() {
        double current = kZero;
        mIntakeRoller.set(ControlMode.PercentOutput, current);
    }
    public void stopWrist() {
        double current = kZero;
        mWrist.setDesiredOutput(current);
    }
    public void stopIntake() {
        stopRoller();
        stopWrist();
    }


    /**
     * Main commands that can be used in DriverInput
     */
    public void commandGroundHatch() {
        mDesiredIntakeState = EIntakeState.GROUND_HATCH;
        System.out.println("****************************************************");
    }
    public void commandGroundCargo() {
        mDesiredIntakeState = EIntakeState.GROUND_CARGO;
    }
    public void commandStowed() {
        mDesiredIntakeState = EIntakeState.STOWED;
    }
    public void commandHandoff() {
        mDesiredIntakeState = EIntakeState.HANDOFF;
    }
    public void commandStop() {
        stopIntake();
    }

    // /**
    //  *
    //  * @return The sensor value indicating whether we have acquired a hatch.
    //  */
    // public boolean hasHatch() {
    //     return mHatchBeam.get();
    // }

    /**
     * Asks if the a given wrist position matches the current wrist position.
     * @param pWristPosition position in question
     * @return Whether the wrist had been at the specified setpoint for a certain amount of time within a certain deadband.
     */
    public boolean isAtPosition(EWristState pWristPosition) {
        return mWristPosition.equals(pWristPosition);
    }

}

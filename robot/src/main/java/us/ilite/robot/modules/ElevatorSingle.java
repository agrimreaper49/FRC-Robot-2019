package us.ilite.robot.modules;

import com.flybotix.hfr.util.log.ILog;
import com.flybotix.hfr.util.log.Logger;
import com.revrobotics.CANPIDController;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMaxLowLevel;
import com.revrobotics.ControlType;
import com.team254.lib.util.Util;
import us.ilite.common.Data;
import us.ilite.common.config.SystemSettings;
import us.ilite.common.types.manipulator.EElevator;
import us.ilite.common.types.sensor.EPowerDistPanel;
import us.ilite.lib.drivers.SparkMaxFactory;

public class ElevatorSingle extends Module{
    private static ElevatorSingle elevatorInstance = new ElevatorSingle();

    private ILog mLog = Logger.createLog(ElevatorSingle.class);

    private CANPIDController mCanController;
    private Data mData;

    private double mSetPoint = 0;
    private double mDesiredPower = 0;
    private boolean mRequestedStop = false;
    private boolean firstTime = true;
    ElevatorSingle.EElevatorState mCurrentState;
    ElevatorSingle.EElevatorPosition mDesiredPosition;
    CANSparkMax mMasterElevator;
//    private boolean mDifferentAcceleration = true;
//    private mLastUp;

    public static ElevatorSingle getInstance() {
        if ( elevatorInstance == null ) {
            elevatorInstance = new ElevatorSingle();
        }
        return elevatorInstance;
    }

    private ElevatorSingle() {
        // Create default NEO
        mMasterElevator = SparkMaxFactory.createDefaultSparkMax( SystemSettings.kElevatorNEOAddress, CANSparkMaxLowLevel.MotorType.kBrushless);
        mMasterElevator.setIdleMode( CANSparkMax.IdleMode.kBrake);
        mMasterElevator.setClosedLoopRampRate(0);
        mMasterElevator.setInverted(true);

        this.mCanController = mMasterElevator.getPIDController();

        mMasterElevator.setOpenLoopRampRate(SystemSettings.kElevatorOpenLoopRampRate);
        mMasterElevator.setSmartCurrentLimit(SystemSettings.kElevatorSmartCurrentLimit);
        mMasterElevator.setSecondaryCurrentLimit(SystemSettings.kElevatorSecondaryCurrentLimit);
        mCanController.setOutputRange(SystemSettings.kElevatorClosedLoopMinPower, SystemSettings.kElevatorClosedLoopMaxPower, SystemSettings.kElevatorSmartMotionSlot);

        //Setting PID Coefficients for Motion Magic
        mCanController.setP(SystemSettings.kElevatorMotionP);
        mCanController.setI(SystemSettings.kElevatorMotionI);
        mCanController.setD(SystemSettings.kElevatorMotionD);
        mCanController.setFF(SystemSettings.kElevatorMotionFF);

        mCanController.setSmartMotionMaxAccel(SystemSettings.kMaxElevatorUpAcceleration, SystemSettings.kElevatorSmartMotionSlot);
        mCanController.setSmartMotionMinOutputVelocity(SystemSettings.kMinElevatorVelocity, SystemSettings.kElevatorSmartMotionSlot);
        mCanController.setSmartMotionMaxVelocity(SystemSettings.kMaxElevatorVelocity, SystemSettings.kElevatorSmartMotionSlot);
        mCanController.setSmartMotionMinOutputVelocity(0, SystemSettings.kElevatorSmartMotionSlot);
        mCanController.setSmartMotionAllowedClosedLoopError(SystemSettings.kElevatorClosedLoopAllowableError, SystemSettings.kElevatorSmartMotionSlot);

        mMasterElevator.burnFlash();


        // Make sure the elevator is stopped upon initialization
        mDesiredPosition = ElevatorSingle.EElevatorPosition.HATCH_BOTTOM;
        mCurrentState = ElevatorSingle.EElevatorState.STOP;
    }

    public void setData( Data pData) {
        this.mData = pData;
    }


    public enum EElevatorState {

        //TODO find all of the values for the power.
        NORMAL(0),
        STOP(0),
        SET_POSITION(0.1);

        private double mPower;

        EElevatorState(double pPower) {
            this.mPower = pPower;
        }

        double getPower() {
            return mPower;
        }

    }

    public enum EElevatorPosition {

        //TODO find encoder threshold
        HATCH_BOTTOM(1),
        HATCH_MIDDLE(17),
        HATCH_TOP(35),
        CARGO_BOTTOM(10.5),
        CARGO_LOADING_STATION(17),
        CARGO_CARGO_SHIP(16.5),
        CARGO_MIDDLE(27.5),
        CARGO_TOP(45);

        private double kEncoderRotations;

        EElevatorPosition(double pEncoderRotations) {
            this.kEncoderRotations = pEncoderRotations;

        }

        public double getEncoderRotations() {
            return kEncoderRotations;
        }
    }


    public void shutdown(double pNow) {

    }

    public void modeInit(double pNow) {
    }

    public void periodicInput(double pNow) {

        mData.elevator.set( EElevator.DESIRED_POWER, mDesiredPower);
//        mData.elevator.set(EElevator.OUTPUT_POWER, mMasterElevator.getAppliedOutput());
        mData.elevator.set(EElevator.DESIRED_ENCODER_TICKS, mSetPoint);
        mData.elevator.set(EElevator.CURRENT_ENCODER_TICKS, getEncoderPosition());
        mData.elevator.set(EElevator.CURRENT, mMasterElevator.getOutputCurrent());
//        mData.elevator.set(EElevator.BUS_VOLTAGE, mMasterElevator.getBusVoltage());
        mData.elevator.set(EElevator.DESIRED_POSITION_TYPE, (double) mDesiredPosition.ordinal());
        mData.elevator.set(EElevator.CURRENT_STATE, (double) mCurrentState.ordinal());

    }

    public void update(double pNow) {

        if ( firstTime ) {
            zeroEncoder();
        }

        switch (mCurrentState) {
            case NORMAL:
                mDesiredPower = Util.limit(mDesiredPower, SystemSettings.kElevatorOpenLoopMinPower, SystemSettings.kElevatorOpenLoopMaxPower);
                mMasterElevator.set(mDesiredPower);
                break;
            case STOP:
                mDesiredPower = 0;
                break;
            case SET_POSITION:
                mSetPoint = mRequestedStop ? mData.elevator.get(EElevator.CURRENT_ENCODER_TICKS) : mDesiredPosition.getEncoderRotations();
                mDesiredPower = 0;
                mCanController.setReference(mSetPoint, ControlType.kSmartMotion, 0, SystemSettings.kElevatorFrictionVoltage);
                gainSchedule();
                break;
            default:
                mLog.error("Somehow reached an unaccounted state with ", mCurrentState.toString());
                mDesiredPower = 0;
                break;
        }

        mRequestedStop = false;
        firstTime = false;
        System.out.println("CURRENT STATE=================================================== " + mCurrentState );
    }

    /**
     * Resets the encoder
     * which sets the amount of ticks
     * to zero
     */
    public void zeroEncoder() {
        mMasterElevator.getEncoder().setPosition(0);
        mData.elevator.set( EElevator.CURRENT_ENCODER_TICKS, 0.0 );
    }


    public void setDesiredPower(double pPower) {
        mCurrentState = ElevatorSingle.EElevatorState.NORMAL;
        mDesiredPower = pPower;
    }

    public double getDesiredPower() {
        return mDesiredPower;
    }

    public double getEncoderPosition() {
        return mMasterElevator.getEncoder().getPosition();
    }

    public ElevatorSingle.EElevatorPosition getDesiredPosition() {
        return mDesiredPosition;
    }

    public ElevatorSingle.EElevatorState getCurrentState() {
        return mCurrentState;
    }

    public void setDesiredPosition( ElevatorSingle.EElevatorPosition pDesiredPosition) {
        mCurrentState = ElevatorSingle.EElevatorState.SET_POSITION;
        mDesiredPosition = pDesiredPosition;
    }

    public boolean isAtPosition( ElevatorSingle.EElevatorPosition pPosition) {
        return mCurrentState == ElevatorSingle.EElevatorState.SET_POSITION && (Math.abs(pPosition.getEncoderRotations() - mData.elevator.get(EElevator.CURRENT_ENCODER_TICKS)) <= SystemSettings.kElevatorAllowableError);
    }

    public boolean isCurrentLimiting() {
        return mData.pdp.get( EPowerDistPanel.CURRENT9) > SystemSettings.kElevatorWarnCurrentLimitThreshold;
    }

    public void gainSchedule() {
        if ( getDesiredPosition().getEncoderRotations() > getEncoderPosition() /*&& mDifferentAcceleration*/) {
            mCanController.setSmartMotionMaxAccel(SystemSettings.kMaxElevatorUpAcceleration, SystemSettings.kElevatorSmartMotionSlot);
        } else /*if ( mDifferentAcceleration ) */{
            mCanController.setSmartMotionMaxAccel(SystemSettings.kMaxElevatorDownAcceleration, SystemSettings.kElevatorSmartMotionSlot);
        }
    }

    public void stop() {
        mRequestedStop = true;
    }
}

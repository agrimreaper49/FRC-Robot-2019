package us.ilite.robot.modules;

import com.flybotix.hfr.codex.Codex;
import com.flybotix.hfr.util.log.ILog;
import com.flybotix.hfr.util.log.Logger;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.Timer;
import us.ilite.common.Data;
import us.ilite.common.config.SystemSettings;
import us.ilite.common.types.drive.EDriveData;
import us.ilite.common.types.manipulator.EHatchGrabber;

/**
 * Control the Hatch Flower
 *
 * The Hatch Capture button will take precedence over the Hatch Push button.
 * Once the Hatch Capture button is pressed, the hatch flower will transition to
 * the CAPTURE state and stay there until the Hatch Push button is pressed.
 * Then the Hatch Flower will push the hatch and go to the RELEASE configuration.
 */
public class HatchFlower extends Module {

    private ILog mLog = Logger.createLog(HatchFlower.class);

    private Data mData;

    private Solenoid mGrabSolenoid;
    private Solenoid mExtendSolenoid;
    private DigitalInput mUpperHatchSwitch;

    private GrabberState mGrabberState;
    private ExtensionState mExtensionState;
    private Timer mHasHatchTimer = new Timer();

    private Codex<Double, EDriveData> mDriveData;
    private Codex<Double, EHatchGrabber> mHatchGrabberData;

    private double mRobotPositionWhenReleased = 0.0;

    /////////////////////////////////////////////////////////////////////////
    // ********************** Solenoid state enums *********************** //
    public enum GrabberState
    {
        GRAB(false),  // set to solenoid state that corresponds with grabbing the hatch
        RELEASE(true);

        private boolean grabber;

        private GrabberState(boolean grabber)
        {
            this.grabber = grabber;
        }
    }

    public enum ExtensionState {
        UP(false),
        DOWN(true);

        private boolean extension;

        ExtensionState(boolean pExtension) {
            extension = pExtension;
        }
    }

    public HatchFlower(Data pData) {
        mData = pData;
        mDriveData = mData.drive;
        mHatchGrabberData = mData.hatchgrabber;

        // TODO Do we need to pass the CAN Addresses in via the constructor?
        mGrabSolenoid = new Solenoid(SystemSettings.kCANAddressPCM, SystemSettings.kHatchFlowerOpenCloseSolenoidAddress);
        mExtendSolenoid = new Solenoid(SystemSettings.kCANAddressPCM, SystemSettings.kHatchFlowerExtensionSolenoidAddress);
        mUpperHatchSwitch = new DigitalInput(SystemSettings.kHatchFlowerHatchSwitchAddress);

        // Init Hatch Flower to grab state - Per JKnight we will start with a hatch or cargo onboard
        this.mGrabberState = GrabberState.GRAB;
        this.mExtensionState = ExtensionState.DOWN;

        this.mGrabSolenoid.set(GrabberState.GRAB.grabber);
        this.mExtendSolenoid.set(ExtensionState.DOWN.extension);
    }

    /**
     *
     */
    @Override
    public void modeInit(double pNow) {
        mLog.error("MODE INIT");
    }

    @Override
    public void periodicInput(double pNow) {
        mData.hatchgrabber.set(EHatchGrabber.EXTENDED, (double)mExtensionState.ordinal());
        mData.hatchgrabber.set(EHatchGrabber.GRABBING, (double)mGrabberState.ordinal());
        mData.hatchgrabber.set(EHatchGrabber.HATCH_SWITCH, isHatchSwitchTriggered() ? 1.0 : 0.0);
        mData.hatchgrabber.set(EHatchGrabber.HAS_HATCH, hasHatch() ? 1.0 : 0.0);
    }

    @Override
    public void update(double pNow) {

        mGrabSolenoid.set(mGrabberState.grabber);
        mExtendSolenoid.set(mExtensionState.extension);

        if(isHatchSwitchTriggered()) {
            mHasHatchTimer.reset();
            mHasHatchTimer.start();
        } else {
            mHasHatchTimer.stop();
        }

    }

    @Override
    public void shutdown(double pNow) {
    }

    @Override
    public boolean checkModule(double pNow) {
        return true; // TODO What does this do?
    }

    /**
     * Configure the solenoids to capture a hatch
     */
    public void captureHatch() {
        mGrabberState = GrabberState.GRAB;
    }

    /**
     * Configure the solenoids to push the hatch
     */
    public void pushHatch() {
        mGrabberState = GrabberState.RELEASE;
        mRobotPositionWhenReleased = getAverageRobotDistanceTraveled();
    }

    /**
     *
     * @return Whether the robot has backed up far enough to allow the elevator to come down.
     */
    public boolean isHatchGrabberSafeToRetract() {
        return mRobotPositionWhenReleased - getAverageRobotDistanceTraveled() >= SystemSettings.kHatchFlowerBackupDistanceInches;
    }

    private double getAverageRobotDistanceTraveled() {
        return (mDriveData.get(EDriveData.LEFT_POS_INCHES) + mDriveData.get(EDriveData.RIGHT_POS_INCHES)) / 2.0;
    }

    /**
     * Sets whether hatch grabber is up or down
     * @param pExtensionState
     */
    public void setFlowerExtended(ExtensionState pExtensionState) {
        mExtensionState = pExtensionState;
    }

    public ExtensionState getExtensionState() {
        return mExtensionState;
    }

    public GrabberState getGrabberState() {
        return mGrabberState;
    }

    /**
     * This should be dictated by sensor value if possible.
     * @return Whether the hatch grabber is currently holding a hatch.
     */
    public boolean hasHatch() {
        return (isHatchSwitchTriggered()) && mHasHatchTimer.hasPeriodPassed(SystemSettings.kHatchFlowerSwitchPressedTime);
    }

    private boolean isHatchSwitchTriggered() {
        return mUpperHatchSwitch.get();
    }


}

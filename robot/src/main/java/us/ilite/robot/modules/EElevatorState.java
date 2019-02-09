package us.ilite.robot.modules;

public enum EElevatorState {

    //TODO find all of the values for the power.
    NORMAL(0),
    STOP(0),
    HOLD(0),
    DECEL_TOP(0),
    DECEL_BOTTOM(0),
    SET_POSITION(0);

    private double mPower;

    EElevatorState(double pPower) {
        this.mPower = pPower;
    }
    
    double getPower() {
        return mPower;
    }

}
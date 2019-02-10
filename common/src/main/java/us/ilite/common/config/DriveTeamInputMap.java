package us.ilite.common.config;


import us.ilite.common.types.input.ELogitech310;

public class DriveTeamInputMap {

    public static final ELogitech310
  
    DRIVER_TURN_AXIS = ELogitech310.RIGHT_X_AXIS,
    DRIVER_THROTTLE_AXIS = ELogitech310.LEFT_Y_AXIS,
//    DRIVER_SUB_WARP_AXIS = ELogitech310.RIGHT_TRIGGER_AXIS,

    DRIVER_NUDGE_SEEK_LEFT = ELogitech310.L_BTN,
    DRIVER_NUDGE_SEEK_RIGHT = ELogitech310.R_BTN,

    DRIVER_TRACK_CARGO_BTN = ELogitech310.X_BTN,
    DRIVER_TRACK_TARGET_BTN = ELogitech310.A_BTN,
    DRIVER_TRACK_HATCH_BTN = ELogitech310.B_BTN,

    // Hatch Flower Control
    DRIVER_HATCH_FLOWER_CAPTURE_BTN = ELogitech310.BACK,
    DRIVER_HATCH_FLOWER_PUSH_BTN = ELogitech310.START,

    MANIPULATOR_MIDDLE_POSITION_ELEVATOR = ELogitech310.B_BTN,
    MANIPULATOR_BOTTOM_POSITION_ELEVATOR = ELogitech310.A_BTN,
    MANIPULATOR_TOP_POSITION_ELEVATOR = ELogitech310.Y_BTN,
            MANIPULATOR_CONTROL_ELEVATOR = ELogitech310.COMBINED_TRIGGER_AXIS;

}

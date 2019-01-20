package us.ilite.robot;

import com.flybotix.hfr.codex.Codex;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import us.ilite.common.LoggedData;
import us.ilite.common.io.CodexNetworkTables;
import us.ilite.common.types.drive.EDriveData;
import us.ilite.common.types.input.EDriverInputMode;
import us.ilite.common.types.input.ELogitech310;
import us.ilite.common.types.sensor.EGyro;
import us.ilite.lib.util.SimpleNetworkTable;

public class Data {

    public LoggedData loggedData = new LoggedData();

    public CodexNetworkTables mCodexNT = CodexNetworkTables.getInstance();
    public Codex<Double, EGyro> imu = loggedData.imu;
    public Codex<Double, EDriveData> drive = loggedData.drive;
    public Codex<Double, ELogitech310> driverinput = Codex.of.thisEnum(ELogitech310.class);
    public Codex<Double, ELogitech310> operatorinput = Codex.of.thisEnum(ELogitech310.class);

    public static NetworkTableInstance kInst = NetworkTableInstance.getDefault();
    public static SimpleNetworkTable kLoggingTable = new SimpleNetworkTable("LoggingTable");
    public static SimpleNetworkTable kSmartDashboard = new SimpleNetworkTable("SmartDashboard");
    public static NetworkTable kLimelight = kInst.getTable("limelight");
    public static SimpleNetworkTable kDriverControlSelection = new SimpleNetworkTable("DriverControlSelection") {
        @Override
        public void initKeys() {
            getInstance().getEntry(EDriverInputMode.class.getSimpleName()).setDefaultNumber(-1);
        }
    };

    public Data() {
        registerCodices();
        sendCodices();
    }

    /**
     * Do this before sending codices to NetworkTables
     */
    public void registerCodices() {
        mCodexNT.registerCodex(EGyro.class);
        mCodexNT.registerCodex(EDriveData.class);
        mCodexNT.registerCodex(ELogitech310.class);
    }
    
    /**
     * Sends Codex entries into its corresponding NetworkTable
     */
    public void sendCodices() {
        mCodexNT.send(imu);
        mCodexNT.send(drive);
        mCodexNT.send(driverinput);
        mCodexNT.send(operatorinput);
    }
}

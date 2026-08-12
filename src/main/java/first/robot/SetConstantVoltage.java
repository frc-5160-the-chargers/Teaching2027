package first.robot;

import org.wpilib.command3.Scheduler;
import org.wpilib.opmode.PeriodicOpMode;
import org.wpilib.opmode.Utility;

@Utility
public class SetConstantVoltage extends PeriodicOpMode {
    private final Robot robot;

    public SetConstantVoltage(Robot robot) {
        this.robot = robot;
    }

    @Override
    public void start() {
        Scheduler.getDefault().schedule(robot.shooter.setConstantVoltage(12));
    }
}

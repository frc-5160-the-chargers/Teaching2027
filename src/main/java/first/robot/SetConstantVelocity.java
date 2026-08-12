package first.robot;

import org.wpilib.command3.Scheduler;
import org.wpilib.opmode.PeriodicOpMode;
import org.wpilib.opmode.Utility;

@Utility
public class SetConstantVelocity extends PeriodicOpMode {
    private final Robot robot;

    public SetConstantVelocity(Robot robot) {
        this.robot = robot;
    }

    @Override
    public void start() {
        Scheduler.getDefault().schedule(robot.shooter.setVelocity(100));
    }
}

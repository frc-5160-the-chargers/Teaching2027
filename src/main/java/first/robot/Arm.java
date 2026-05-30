package first.robot;

import com.revrobotics.spark.SparkLowLevel;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.util.Signal;
import org.wpilib.command3.Command;
import org.wpilib.command3.Mechanism;

public class Arm extends Mechanism {
    private final SparkMax motor = new SparkMax(0, 0, SparkLowLevel.MotorType.kBrushless);
    private final Signal<Double> positionSignal = motor.getEncoder().getPosition();
    // I require the arm motor
    // I require the arm mechanism

    public Arm() {
//        Command runArmAt7Volts = run(coroutine -> {
//            // this method is using the armMotor
//            // this method REQUIRES the arm
//            while (positionSignal.get() < 5) {
//                motor.setVoltage(7.0);
//                coroutine.yield();
//            }
//            motor.setVoltage(0.0);
//        })
//            .named("Run Arm at 7 volts");
//
//        Command runArmAt5Volts = run(coroutine -> {
//            // this method uses the armMotor
//            while (positionSignal.get() < 5) {
//                motor.setVoltage(5.0);
//                coroutine.yield();
//            }
//            motor.setVoltage(0.0);
//        })
//            .named("Run Arm at 5 volts");
//
//        // whileTrue - doesn't run the shooter over and over again
//        xbox.dpadUp().whileTrue(runArmAt5Volts);
//        xbox.rightBumper().whileTrue(runArmAt7Volts);
    }

    public Command runArmAt7Volts() {
        return run(coroutine -> {
            // this method is using the armMotor
            // this method REQUIRES the arm
            while (positionSignal.get() < 5) {
                motor.setVoltage(7.0);
                coroutine.yield();
            }
            motor.setVoltage(0.0);
        })
            .named("Run Arm at 7 volts");
    }

    public Command printHiAndRunArmAt7Volts() {
        return run(coroutine -> {
            System.out.println("Hi!");
            while (true) {
                coroutine.await(runArmAt7Volts());
            }
        })
            .named("Print Hi and Run Arm at 7 volts");
    }
}

package first.robot;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.hardware.TalonFX;
import org.wpilib.command3.Command;
import org.wpilib.command3.Mechanism;

import static org.wpilib.units.Units.RadiansPerSecond;

public class Shooter extends Mechanism {

    private final TalonFX motor = new TalonFX(0, CANBus.systemcore(0));
    private static final double KP = 0;
    private static final double KV = 0;

    public Shooter(){

    }

    //sets motor to constant voltage
    public Command setConstantVoltage(double voltage){
        return run(coroutine -> {
            while(true){
                coroutine.yield();
                motor.setVoltage(voltage);
            }

        }).named("Set Motor to Constant Voltage");
    }

    public Command setVelocity(double radiansPerSecond){
        return run(coroutine -> {
            while(true){
                coroutine.yield();
                double currentVelocity = motor.getVelocity().getValue().in(RadiansPerSecond);
                double voltage = KP * (radiansPerSecond - currentVelocity) + KV * radiansPerSecond;
                motor.setVoltage(voltage);
            }

        }).named("Set Velocity");
    }


}

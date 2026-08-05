import org.example.HasUnit;
import org.example.OverrideUnit;

public class Hi {


    {
        @HasUnit("volts/(meters/seconds)") double kP = 0.1;
        @HasUnit("meters") double distance = 0.1;
        @HasUnit("s") double time = 0.1;
        @HasUnit("volts") double feedforward = 5.0;

        double velocity = distance / time;
        double voltage = kP * velocity;
        voltage += feedforward;
    }
}

import org.example.HasUnit;

public class Hi {
    @HasUnit("volts/(m/s)") double a = 0.1;
    @HasUnit("m/s") double c = 1;
    @HasUnit("volts") double b = 0;

    {
        b += a * c;
    }
}

import org.example.HasUnit;

public class Hi {
    @HasUnit("volts * s") double a = 0.1;
    @HasUnit("s") double b = 1;

    {
        @HasUnit("meters") double hi = 10;

        double test = 1 + 2;
        double shouldWork = hi + a / b;
    }
}

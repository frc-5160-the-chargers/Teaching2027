import org.example.HasUnit;

public class Hi {
    @HasUnit("meters/second") double mm = 0.1;
    @HasUnit("m/s") double m = 1;

    {
        double bad = mm + m;
    }
}

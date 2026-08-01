import org.example.HasUnit;
import org.example.OverrideUnit;

public class Hi {
    @HasUnit("m") double a = 0.1;

    {
        @OverrideUnit("s") double time = a;
    }
}

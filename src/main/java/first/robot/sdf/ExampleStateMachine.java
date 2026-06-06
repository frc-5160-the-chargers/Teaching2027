package first.robot.sdf;

import first.util.MakeStateMachineGraph;
import first.util.StateMachine;
import first.util.StateMachine.State;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.wpilib.command3.Command;
import org.wpilib.command3.Coroutine;
import org.wpilib.command3.Mechanism;
import org.wpilib.command3.Trigger;

/**
 * Example state machine for demonstration and testing purposes. This class creates a simple
 * arm/elevator state machine that can be visualized using the Gradle plugin.
 */
public class ExampleStateMachine {
  private static boolean someCond() {
    return false;
  }

  private static int number = 0;
  private static final Trigger someTrigger = new Trigger(() -> false);

  @MakeStateMachineGraph
  public static StateMachine testLambdas() {
    // Create simple boolean suppliers for transitions
    BooleanSupplier moveToPickup = () -> false;
    BooleanSupplier pickupComplete = () -> false;
    BooleanSupplier moveToScore = () -> false;
    BooleanSupplier scoreComplete = () -> false;
    BooleanSupplier reset = () -> false;

    // Build the state machine
    StateMachine sm = new StateMachine("Test Lambdas");

    State home = sm.addState(named("Home"));
    State pickupPosition = sm.addState(named("Pickup Position"));
    State picking = sm.addState(named("Picking"));
    State scorePosition = sm.addState(named("Score Position"));
    State scoring = sm.addState(named("Scoring"));

    sm.setInitialState(home);
    home.switchTo(() -> switch (number) {
      case 0 -> {
        System.out.println("Not Allowed LOL");
        yield home;
      }
      default -> pickupPosition;
    })
    .whenComplete();

    return sm;
  }

  private static Command named(String name) {
    return new Command() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public Set<Mechanism> requirements() {
        return Set.of();
      }

      @Override
      public void run(Coroutine coroutine) {
        coroutine.yield();
      }
    };
  }
}

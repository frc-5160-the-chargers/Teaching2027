package first.robot.sdf;

import first.util.GenerateDiagram;
import first.util.StateMachine;
import first.util.StateMachine.State;
import org.wpilib.command3.Command;
import org.wpilib.command3.Coroutine;
import org.wpilib.command3.Mechanism;
import org.wpilib.command3.Trigger;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Example state machine for demonstration and testing purposes.
 * This class creates a simple arm/elevator state machine that can be visualized
 * using the Gradle plugin.
 */
public class ExampleStateMachine {

    /**
     * Builds an example state machine demonstrating transitions.
     * This method is called by the Gradle task to generate the diagram.
     */
    @GenerateDiagram
    public static StateMachine buildStateMachine() {
        // Create simple boolean suppliers for transitions
        BooleanSupplier moveToPickup = () -> false;
        BooleanSupplier pickupComplete = () -> false;
        BooleanSupplier moveToScore = () -> false;
        BooleanSupplier scoreComplete = () -> false;
        BooleanSupplier reset = () -> false;

        // Build the state machine
        StateMachine sm = new StateMachine("Example Arm and Elevator");

        State home = sm.addState(named("Home"));
        State pickupPosition = sm.addState(named("Pickup Position"));
        State picking = sm.addState(named("Picking"));
        State scorePosition = sm.addState(named("Score Position"));
        State scoring = sm.addState(named("Scoring"));

        // Define transitions
        home.switchTo(pickupPosition).whenCompleteAnd(moveToPickup);
        pickupPosition.switchTo(picking).when(pickupComplete);
        picking.switchTo(home).when(reset);

        home.switchTo(scorePosition).when(moveToScore);
        scorePosition.switchTo(scoring).when(scoreComplete);
        scoring.switchTo(home).when(reset);

        // Set initial state
        sm.setInitialState(home);

        return sm;
    }

    private static boolean someCond() { return false; }

    private static int number = 0;
    private static final Trigger someTrigger = new Trigger(() -> false);

    @GenerateDiagram
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
        sm.switchFromAny(home, scoring)
            .to(() -> !someCond() ? pickupPosition : scorePosition)
            .whenComplete();

        return sm;
    }

    private static Command named(String name) {
        return new Command() {
            @Override public String name() { return name; }
            @Override public Set<Mechanism> requirements() { return Set.of(); }
            @Override public void run(Coroutine coroutine) { coroutine.yield(); }
        };
    }
}


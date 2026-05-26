package first.robot.sdf;

import first.util.GenerateDiagram;
import org.wpilib.command3.Command;
import org.wpilib.command3.Coroutine;
import org.wpilib.command3.Mechanism;
import org.wpilib.command3.StateMachine;

import java.util.Set;
import java.util.function.BooleanSupplier;

public class OPStateMachines {
    @GenerateDiagram
    public static StateMachine team2056TeleopStateMachine() {
        BooleanSupplier homeButton = () -> false;
        BooleanSupplier coralPickupButton = () -> false;
        BooleanSupplier hasCoral = () -> false;

        BooleanSupplier hasAlgae = () -> false;
        BooleanSupplier homeButtonAndHasAlgae = () -> homeButton.getAsBoolean() && hasAlgae.getAsBoolean();
        BooleanSupplier homeButtonAndHasNoAlgae = () -> homeButton.getAsBoolean() && !hasAlgae.getAsBoolean();

        BooleanSupplier l1ScoreButton = () -> false;
        BooleanSupplier l2ScoreButton = () -> false;
        BooleanSupplier l3ScoreButton = () -> false;
        BooleanSupplier l4ScoreButton = () -> false;

        BooleanSupplier l1ScoreButtonAndHasCoral = () -> l1ScoreButton.getAsBoolean() && hasCoral.getAsBoolean();
        BooleanSupplier l2ScoreButtonAndHasCoral = () -> l2ScoreButton.getAsBoolean() && hasCoral.getAsBoolean();
        BooleanSupplier l3ScoreButtonAndHasCoral = () -> l3ScoreButton.getAsBoolean() && hasCoral.getAsBoolean();
        BooleanSupplier l4ScoreButtonAndHasCoral = () -> l4ScoreButton.getAsBoolean() && hasCoral.getAsBoolean();

        BooleanSupplier l1ScoreButtonAndHasAlgae = () -> l1ScoreButton.getAsBoolean() && hasAlgae.getAsBoolean();
        BooleanSupplier l4ScoreButtonAndHasAlgae = () -> l4ScoreButton.getAsBoolean() && hasAlgae.getAsBoolean();

        BooleanSupplier algaeFloorPickupButton = () -> false;

        BooleanSupplier scoreButton = () -> false;

        var sm = new StateMachine("Arm and Elevator");

        StateMachine.State home = sm.addState(named("Home"));
        StateMachine.State coralPickup = sm.addState(named("Coral Pickup"));
        sm.setInitialState(home);
        home.switchTo(coralPickup).when(coralPickupButton);
        coralPickup.switchTo(home).when(homeButton);

        StateMachine.State l1Score = sm.addState(named("L1 Score"));
        StateMachine.State l2Score = sm.addState(named("L2 Score"));
        StateMachine.State l3Score = sm.addState(named("L3 Score"));
        StateMachine.State l4Score = sm.addState(named("L4 Score"));
        StateMachine.State spitScoreCoral = sm.addState(named("Spit Score Coral"));
        StateMachine.State l2LowerScore = sm.addState(named("L2 Lower Score"));
        StateMachine.State l3LowerScore = sm.addState(named("L3 Lower Score"));
        StateMachine.State l4LowerScore = sm.addState(named("L4 Lower Score"));

        home.switchTo(l1Score).when(l1ScoreButtonAndHasCoral);
        home.switchTo(l2Score).when(l2ScoreButtonAndHasCoral);
        home.switchTo(l3Score).when(l3ScoreButtonAndHasCoral);
        home.switchTo(l4Score).when(l4ScoreButtonAndHasCoral);

        l1Score.switchTo(spitScoreCoral).when(scoreButton);
        l2Score.switchTo(spitScoreCoral).when(scoreButton);
        l3Score.switchTo(spitScoreCoral).when(scoreButton);
        l4Score.switchTo(spitScoreCoral).when(scoreButton);

        sm.switchFromAny(spitScoreCoral, l2LowerScore, l3LowerScore, l4LowerScore)
                .to(home)
                .when(homeButton);

        StateMachine.State algaeHome = sm.addState(named("Algae Home"));
        StateMachine.State algaeFloorPickup = sm.addState(named("Algae Floor Pickup"));
        StateMachine.State l2AlgaePickup = sm.addState(named("L2 Algae Pickup"));
        StateMachine.State l3AlgaePickup = sm.addState(named("L3 Algae Pickup"));
        StateMachine.State processorScorePosition = sm.addState(named("Processor Score Position"));
        StateMachine.State netScorePosition = sm.addState(named("Net Score Position"));

        home.switchTo(algaeHome).when(algaeFloorPickupButton);
        home.switchTo(l2AlgaePickup).when(l2ScoreButton);
        home.switchTo(l3AlgaePickup).when(l3ScoreButton);

        sm.switchFromAny(algaeFloorPickup, l2AlgaePickup, l3AlgaePickup)
                .to(algaeHome)
                .when(homeButtonAndHasAlgae);

        algaeHome.switchTo(netScorePosition).when(l4ScoreButtonAndHasAlgae);
        algaeHome.switchTo(processorScorePosition).when(l1ScoreButtonAndHasAlgae);

        netScorePosition.switchTo(algaeHome).when(homeButtonAndHasAlgae);
        netScorePosition.switchTo(processorScorePosition).when(l1ScoreButtonAndHasAlgae);
        netScorePosition.switchTo(home).when(homeButtonAndHasNoAlgae);

        processorScorePosition.switchTo(algaeHome).when(homeButtonAndHasAlgae);
        processorScorePosition.switchTo(netScorePosition).when(l4ScoreButtonAndHasAlgae);
        processorScorePosition.switchTo(home).when(homeButtonAndHasNoAlgae);

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

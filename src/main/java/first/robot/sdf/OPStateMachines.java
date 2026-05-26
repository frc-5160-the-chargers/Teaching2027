package first.robot.sdf;

import first.util.GenerateDiagram;
import org.wpilib.command3.*;

import java.util.Set;
import java.util.function.BooleanSupplier;

public class OPStateMachines {
    private static boolean someCond() { return false; }

    private static int number = 0;
    private static final Trigger someTrigger = new Trigger(() -> false);

    @GenerateDiagram
    public static StateMachine team2056TeleopStateMachine() {
        var hasCoral = new Trigger(() -> false);
        var hasAlgae = new Trigger(() -> false);

        var homeButton = new Trigger(() -> false);
        var coralPickupButton = new Trigger(() -> false);
        var l1ScoreButton = new Trigger(() -> false);;
        var l2ScoreButton = new Trigger(() -> false);
        var l3ScoreButton = new Trigger(() -> false);
        var l4ScoreButton = new Trigger(() -> false);
        var algaeFloorPickupButton = new Trigger(() -> false);
        var scoreButton = new Trigger(() -> false);

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

        home.switchTo(l1Score).when(l1ScoreButton.and(hasCoral));
        home.switchTo(l2Score).when(l2ScoreButton.and(hasCoral));
        home.switchTo(l3Score).when(l3ScoreButton.and(hasCoral));
        home.switchTo(l4Score).when(l4ScoreButton.and(hasCoral));

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
                .when(homeButton.and(hasAlgae));

        algaeHome.switchTo(netScorePosition).when(l4ScoreButton.and(hasAlgae));
        algaeHome.switchTo(processorScorePosition).when(l1ScoreButton.and(hasAlgae));

        netScorePosition.switchTo(algaeHome).when(homeButton.and(hasAlgae));
        netScorePosition.switchTo(processorScorePosition).when(l1ScoreButton.and(hasAlgae));
        netScorePosition.switchTo(home).when(homeButton.and(hasAlgae.negate()));
        netScorePosition.switchTo(() -> {
            if (someCond()) {
                return switch (number) {
                    case 0 -> l2LowerScore;
                    case 1 -> l3LowerScore;
                    case 2 -> l4LowerScore;
                    default -> l2LowerScore;
                };
            } else {
                return l3LowerScore;
            }
        })
            .whenComplete();


        processorScorePosition.switchTo(algaeHome).when(homeButton.and(hasAlgae));
        processorScorePosition.switchTo(netScorePosition).when(l4ScoreButton.and(hasAlgae));
        processorScorePosition.switchTo(home).when(homeButton.and(hasAlgae.negate()));

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

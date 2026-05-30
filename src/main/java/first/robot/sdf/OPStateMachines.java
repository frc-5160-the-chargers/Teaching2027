package first.robot.sdf;

import first.util.MakeStateMachineGraph;
import first.util.StateMachine;
import org.wpilib.command3.Command;
import org.wpilib.command3.Coroutine;
import org.wpilib.command3.Mechanism;
import org.wpilib.command3.button.CommandNiDsXboxController;

import java.util.Set;

public class OPStateMachines {
    private static final CommandNiDsXboxController xbox = new CommandNiDsXboxController(0);

    @MakeStateMachineGraph
    public static StateMachine team2056TeleopStateMachine() {
        var hasCoral = xbox.leftTrigger();
        var hasAlgae = xbox.rightTrigger();

        var homeButton = xbox.leftBumper();
        var coralPickupButton = xbox.rightBumper();
        var l1ScoreButton = xbox.povDown();
        var l2ScoreButton = xbox.povLeft();
        var l3ScoreButton = xbox.povUp();
        var l4ScoreButton = xbox.povRight();
        var algaeFloorPickupButton = xbox.a();
        var scoreButton = xbox.y();

        var sm = new StateMachine("2056 Teleop State Machine");

        var home = sm.addState(named("Home"));
        var coralPickup = sm.addState(named("Coral Pickup"));
        sm.setInitialState(home);
        home.switchTo(coralPickup).when(coralPickupButton);
        coralPickup.switchTo(home).when(homeButton);

        var l1Score = sm.addState(named("L1 Score"));
        var l2Score = sm.addState(named("L2 Score"));
        var l3Score = sm.addState(named("L3 Score"));
        var l4Score = sm.addState(named("L4 Score"));
        var spitScoreCoral = sm.addState(named("Spit Score Coral"));
        var l2LowerScore = sm.addState(named("L2 Lower Score"));
        var l3LowerScore = sm.addState(named("L3 Lower Score"));
        var l4LowerScore = sm.addState(named("L4 Lower Score"));

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

        var algaeHome = sm.addState(named("Algae Home"));
        var algaeFloorPickup = sm.addState(named("Algae Floor Pickup"));
        var l2AlgaePickup = sm.addState(named("L2 Algae Pickup"));
        var l3AlgaePickup = sm.addState(named("L3 Algae Pickup"));
        var processorScorePosition = sm.addState(named("Processor Score Position"));
        var netScorePosition = sm.addState(named("Net Score Position"));

        home.switchTo(algaeHome).when(algaeFloorPickupButton);
        home.switchTo(l2AlgaePickup).when(l2ScoreButton);
        home.switchTo(l3AlgaePickup).when(l3ScoreButton);

        sm.switchFromAny(algaeFloorPickup, l2AlgaePickup, l3AlgaePickup)
                .to(algaeHome)
                .when(homeButton.and(hasAlgae));

        algaeHome.switchTo(netScorePosition).when(l4ScoreButton.and(hasAlgae));
        algaeHome.switchTo(processorScorePosition).when(l1ScoreButton.and(hasAlgae));

        netScorePosition.switchTo(algaeHome).when(homeButton.and(hasAlgae));
        netScorePosition.switchTo(home).when(homeButton.and(hasAlgae.negate()));
        netScorePosition.switchTo(processorScorePosition).when(l1ScoreButton.and(hasAlgae));

        processorScorePosition.switchTo(algaeHome).when(homeButton.and(hasAlgae));
        processorScorePosition.switchTo(netScorePosition).when(l4ScoreButton.and(hasAlgae));
        processorScorePosition.switchTo(home).when(homeButton.and(hasAlgae.negate()));

        return sm;
    }

    private static Command named(String name) {
        return new Command() {
            @Override public String name() { return name; }
            @Override public Set<Mechanism> requirements() { return Set.of(); }
            @Override public void run(Coroutine coroutine) {
                while (true) coroutine.yield();
            }
        };
    }
}

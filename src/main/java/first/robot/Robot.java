// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.robot;

import first.util.GraphLogger;
import first.util.MakeStateMachineGraph;
import first.util.StateMachine;
import org.wpilib.command3.Command;
import org.wpilib.command3.Coroutine;
import org.wpilib.command3.Scheduler;
import org.wpilib.command3.button.CommandNiDsXboxController;
import org.wpilib.command3.button.RobotModeTriggers;
import org.wpilib.epilogue.logging.EpilogueBackend;
import org.wpilib.epilogue.logging.NTEpilogueBackend;
import org.wpilib.framework.TimedRobot;
import org.wpilib.networktables.NetworkTableInstance;

/**
 * The methods in this class are called automatically corresponding to each mode, as described in
 * the TimedRobot documentation. If you change the name of this class or the package after creating
 * this project, you must also update the Main.java file in the project.
 */
public class Robot extends TimedRobot {
  private final EpilogueBackend logger = new NTEpilogueBackend(NetworkTableInstance.getDefault());
  private final CommandNiDsXboxController xbox = new CommandNiDsXboxController(0);

  /**
   * This function is run when the robot is first started up and should be used for any
   * initialization code.
   */
  public Robot() { // once
    GraphLogger.getDefault().start(logger::log, logger::log);
    RobotModeTriggers.teleop().whileTrue(exampleStateMachine());
  }

  /**
   * This function is called every 20 ms, no matter the mode. Use this for items like diagnostics
   * that you want ran during disabled, autonomous, teleoperated and utility.
   *
   * <p>This runs after the mode specific periodic functions, but before LiveWindow and
   * SmartDashboard integrated updating.
   */
  @Override
  public void robotPeriodic() {
    Scheduler.getDefault().run();
    logger.log("Scheduler", Scheduler.getDefault(), Scheduler.proto);
  }

  @MakeStateMachineGraph
  public StateMachine exampleStateMachine() {
    var sm = new StateMachine("Example State Machine");
    var a = sm.addState(named("State A"));
    var b = sm.addState(named("State B"));
    var c = sm.addState(named("State C"));
    var d = sm.addState(named("State D"));
    sm.setInitialState(a);
    a.switchTo(b).when(xbox.a());
    b.switchTo(c).when(xbox.b());
    c.switchTo(d).when(xbox.x());
    d.switchTo(a).when(xbox.y());
    return sm;
  }

  @MakeStateMachineGraph
  public StateMachine team2056TeleopStateMachine() {
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
    return Command.noRequirements(Coroutine::park).named(name);
  }
}

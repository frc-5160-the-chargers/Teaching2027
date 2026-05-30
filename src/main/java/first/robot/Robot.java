// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.robot;

import first.robot.sdf.OPStateMachines;
import first.util.GraphLogger;
import org.wpilib.command3.Scheduler;
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

  /**
   * This function is run when the robot is first started up and should be used for any
   * initialization code.
   */
  public Robot() { // once
    GraphLogger.getDefault().start(logger::log);
    RobotModeTriggers.teleop()
      .whileTrue(OPStateMachines.team2056TeleopStateMachine());
  }

  /**
   * This function is called every 20 ms, no matter the mode. Use this for items like diagnostics
   * that you want ran during disabled, autonomous, teleoperated and utility.
   *
   * <p>This runs after the mode specific periodic functions, but before LiveWindow and
   * SmartDashboard integrated updating.
   */
  @Override
  public void robotPeriodic() { // 0.02
    Scheduler.getDefault().run();
    logger.log("Scheduler", Scheduler.getDefault(), Scheduler.proto);
  }
}

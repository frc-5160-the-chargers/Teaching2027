// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.robot;

import first.robot.sdf.OPStateMachines;
import org.wpilib.command3.Scheduler;
import org.wpilib.command3.button.RobotModeTriggers;
import org.wpilib.framework.TimedRobot;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.ProtobufPublisher;
import org.wpilib.networktables.ProtobufTopic;


/**
 * The methods in this class are called automatically corresponding to each mode, as described in
 * the TimedRobot documentation. If you change the name of this class or the package after creating
 * this project, you must also update the Main.java file in the project.
 */
public class Robot extends TimedRobot {
  private final ProtobufPublisher<Scheduler> schedulerLogger = NetworkTableInstance.getDefault().getProtobufTopic("Scheduler", Scheduler.proto).publish();

  /**
   * This function is run when the robot is first started up and should be used for any
   * initialization code.
   */
  public Robot() {
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
  public void robotPeriodic() {
    Scheduler.getDefault().run();
    schedulerLogger.accept(Scheduler.getDefault());
  }
}

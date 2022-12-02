// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.TalonFXSimCollection;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.sensors.CANCoder;

public class SwerveModuleSubsystem extends SubsystemBase {
  private static final double kWheelRadius = 0.0508;
  private static final int kEncoderResolution = 2048;
  private static final double kSensorGearRatio = 1;

  private static final double kModuleMaxAngularVelocity = DrivetrainSubsytem.kMaxAngularSpeed;
  private static final double kModuleMaxAngularAcceleration =
      2 * Math.PI; // radians per second squared

  private final WPI_TalonFX m_driveMotor;
  private final WPI_TalonFX m_turningMotor;
  private final CANCoder m_cCoder;

  private final TalonFXSimCollection m_driveMotorSim;
  private final TalonFXSimCollection m_turningMotorSim;
  private double m_driveVoltage;
  private double m_turningVoltage;

  // Gains are for example purposes only - must be determined for your own robot!
  private final PIDController m_drivePIDController = new PIDController(1, 0, 0);

  // Gains are for example purposes only - must be determined for your own robot!
  private final PIDController m_turningPIDController = new PIDController(1, 0, 0);
      /*new ProfiledPIDController(
          1,
          0,
          0,
          new TrapezoidProfile.Constraints(
              kModuleMaxAngularVelocity, kModuleMaxAngularAcceleration));*/

  // Gains are for example purposes only - must be determined for your own robot!
  private final SimpleMotorFeedforward m_driveFeedforward = new SimpleMotorFeedforward(1, 3);
  private final SimpleMotorFeedforward m_turnFeedforward = new SimpleMotorFeedforward(1, 0.5);

  /**
   * Constructs a SwerveModule with a drive motor, turning motor, drive encoder and turning encoder.
   *
   * @param driveMotorChannel PWM output for the drive motor.
   * @param turningMotorChannel PWM output for the turning motor.
   */
  public SwerveModuleSubsystem(
      int driveMotorChannel,
      int turningMotorChannel,
      int cCoderChannel) {
    this.m_driveMotor = new WPI_TalonFX(driveMotorChannel);
    this.m_turningMotor = new WPI_TalonFX(turningMotorChannel);
    this.m_cCoder = new CANCoder(cCoderChannel);

    this.m_driveMotorSim = m_driveMotor.getSimCollection();
    this.m_turningMotorSim = m_turningMotor.getSimCollection();
    this.m_driveVoltage = 0; //Tracking for simulation
    this.m_turningVoltage = 0; //Tracking for simulation

    // Limit the PID Controller's input range between -pi and pi and set the input
    // to be continuous.
    this.m_turningPIDController.enableContinuousInput(-Math.PI, Math.PI);
  }

  /**
   * Returns the current state of the module.
   *
   * @return The current state of the module.
   */
  public SwerveModuleState getState() {
    return new SwerveModuleState(getDriveVelocity(), new Rotation2d(getTurningDistance()));
  }
  
  public void zeroModule() {
    setDesiredState(new SwerveModuleState(0d, new Rotation2d(0)));
  }

  /**
   * Sets the desired state for the module.
   *
   * @param desiredState Desired state with speed and angle.
   */
  public void setDesiredState(SwerveModuleState desiredState) {
    // Optimize the reference state to avoid spinning further than 90 degrees
    SwerveModuleState state =
        SwerveModuleState.optimize(desiredState, new Rotation2d(getTurningDistance()));

    // Calculate the drive output from the drive PID controller.
    final double driveOutput =
        12*state.speedMetersPerSecond/(2*Math.PI*kWheelRadius*kWheelRadius*(6380/60));//m_drivePIDController.calculate(getDriveVelocity(), state.speedMetersPerSecond);

    final double driveFeedforward = 0;//m_driveFeedforward.calculate(state.speedMetersPerSecond);

    // Calculate the turning motor output from the turning PID controller.
    final double turnOutput =
        m_turningPIDController.calculate(getTurningDistance(), state.angle.getRadians());

    final double turnFeedforward =
        0;//m_turnFeedforward.calculate(m_turningPIDController.getSetpoint().velocity);

    m_driveMotor.setVoltage(driveOutput + driveFeedforward);
    m_turningMotor.setVoltage(turnOutput + turnFeedforward);
    
    m_driveVoltage = driveOutput + driveFeedforward;
    m_turningVoltage = turnOutput + turnFeedforward;
  }

  @Override
  public void periodic() {
    //6380 is max motor rpm
    m_driveMotorSim.setIntegratedSensorRawPosition(
      (int)(
        m_driveMotor.getSensorCollection().getIntegratedSensorPosition() +
        (m_driveVoltage / 12) * (kEncoderResolution*6380/60) * 0.02
      )
    );
    m_driveMotorSim.setIntegratedSensorVelocity((int)((m_driveVoltage / 12) * (kEncoderResolution*6380/60) * 0.02));

    m_turningMotorSim.setIntegratedSensorRawPosition(
      (int)(
        m_turningMotor.getSensorCollection().getIntegratedSensorPosition() +
        (m_turningVoltage / 12) * (kEncoderResolution*6380/60) * 0.02
      )
    );
    m_turningMotorSim.setIntegratedSensorVelocity((int)((m_turningVoltage / 12) * (kEncoderResolution*6380/60) * 0.02));

    final double turnOutput =
        m_turningPIDController.calculate(getTurningDistance());
    m_turningMotor.setVoltage(turnOutput);
    m_turningVoltage = turnOutput;
  }

  /**
   * Gets the encoder value from the drive motor in real world distance.
   * 
   * @return Drive motor position.
   */
  private double getDriveDistance() {
    // Multiplies encoder ticks by distance per encoder tick.
    return m_driveMotor.getSensorCollection().getIntegratedSensorPosition() * 2 * Math.PI * kWheelRadius / kEncoderResolution;
  }

  /**
   * Gets the encoder velocity from the drive motor in real world velocity.
   * 
   * @return Drive motor velocity.
   */
  private double getDriveVelocity() {
    // Multiplies encoder ticks per 100ms by distance per encoder tick.
    return m_driveMotor.getSensorCollection().getIntegratedSensorVelocity() * 2 * Math.PI * kWheelRadius / kEncoderResolution;
  }

  /**
   * Gets the encoder value from the drive motor in radians.
   * 
   * @return Turning motor position.
   */
  private double getTurningDistance() {
    // Multiplies encoder ticks by radians per encoder tick.
    return m_turningMotor.getSensorCollection().getIntegratedSensorPosition() * 2 * Math.PI / kEncoderResolution;
  }

  /**
   * Gets the encoder velocity from the drive motor in radians per second.
   * 
   * @return Turning motor velocity.
   */
  private double getTurningVelocity() {
    // Multiplies encoder ticks per 100ms by radians per encoder tick.
    return m_turningMotor.getSensorCollection().getIntegratedSensorVelocity() * 2 * Math.PI / kEncoderResolution;
  }

  /**
   * Converts meters to encoder counts.
   * @param positionMeters
   * @return Encoder counts.
   */
  private int distanceToNativeUnits(double positionMeters){
    double wheelRotations = positionMeters/(2 * Math.PI * kWheelRadius);
    double motorRotations = wheelRotations * kSensorGearRatio;
    int sensorCounts = (int)(motorRotations * kEncoderResolution);
    return sensorCounts;
  }

  /**
   * Converts Mps to encoder counts per 100 ms.
   * @param velocityMetersPerSecond
   * @return Encoder counts per 100ms.
   */
  private int velocityToNativeUnits(double velocityMetersPerSecond){
    double wheelRotationsPerSecond = velocityMetersPerSecond/(2 * Math.PI * kWheelRadius);
    double motorRotationsPerSecond = wheelRotationsPerSecond * kSensorGearRatio;
    double motorRotationsPer100ms = motorRotationsPerSecond / 10;
    int sensorCountsPer100ms = (int)(motorRotationsPer100ms * kEncoderResolution);
    return sensorCountsPer100ms;
  }

  /**
   * Converts encoder counts to meters.
   * @param sensorCounts
   * @return Meters.
   */
  private double nativeUnitsToDistanceMeters(double sensorCounts){
    double motorRotations = (double)sensorCounts / kEncoderResolution;
    double wheelRotations = motorRotations / kSensorGearRatio;
    double positionMeters = wheelRotations * (2 * Math.PI * kWheelRadius);
    return positionMeters;
  }
}
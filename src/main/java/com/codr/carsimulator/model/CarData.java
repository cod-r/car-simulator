package com.codr.carsimulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarData {
    private float coolantTemp;
    private float intakeAirTemp;
    private float intakeAirFlowSpeed;
    private float batteryPercentage;
    private float batteryVoltage;
    private float currentDraw;
    private float speed;
    private float engineVibrationAmplitude;
    private float throttlePos;
    private int tirePressure11;
    private int tirePressure12;
    private int tirePressure21;
    private int tirePressure22;
    private float accelerometer11Value;
    private float accelerometer12Value;
    private float accelerometer21Value;
    private float accelerometer22Value;
    private int controlUnitFirmware;
    private String failureOccurred;
}

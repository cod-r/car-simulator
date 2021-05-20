package com.codr.carsimulator.cron;

import com.codr.carsimulator.model.CarModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
//@EnableScheduling
public class Cron {

    @Autowired
    CarModel carModel;

    @Scheduled(fixedRate = 10000)
    public void generateMqttPayload() {
        System.out.println(carModel.nextValue());
    }
}

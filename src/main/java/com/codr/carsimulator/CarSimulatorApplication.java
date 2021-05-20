package com.codr.carsimulator;

import com.codr.carsimulator.model.CarData;
import com.codr.carsimulator.model.CarModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SpringBootApplication
public class CarSimulatorApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(CarSimulatorApplication.class, args);

        final List<CarModel> cars = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> new CarModel())
                .collect(Collectors.toList());

        for (int i = 0; i < Integer.MAX_VALUE; ++i) {
            cars.forEach(carModel -> {
                try {
                    CarData carData = carModel.nextValue();
                    System.out.println(carData);

                    String publisherId = UUID.randomUUID().toString();

                    IMqttClient publisher = new MqttClient("tcp://localhost:1883", publisherId);

                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setAutomaticReconnect(true);
                    options.setCleanSession(true);
                    options.setConnectionTimeout(10);
                    publisher.connect(options);

                    ObjectMapper objectMapper = new ObjectMapper();
                    MqttMessage msg = new MqttMessage(objectMapper.writeValueAsBytes(carData));
                    msg.setQos(0);
                    msg.setRetained(true);
                    publisher.publish("/testing", msg);

                } catch (MqttException | JsonProcessingException e) {
                    e.printStackTrace();
                }
            });


            Thread.sleep(10000);
        }
    }
}

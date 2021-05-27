package com.codr.carsimulator;

import com.codr.carsimulator.model.CarData;
import com.codr.carsimulator.model.CarModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SpringBootApplication
public class CarSimulatorApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(CarSimulatorApplication.class, args);

        int numberOfCars = 2;
        final Map<CarModel, IMqttClient> cars = IntStream.rangeClosed(1, numberOfCars)
                .mapToObj(i -> new CarModel())
                .collect(Collectors.toMap(Function.identity(), carModel -> {
                    try {
                        IMqttClient mqttClient = new MqttClient("tcp://localhost:1883", carModel.getCarId());
                        MqttConnectOptions options = new MqttConnectOptions();
                        options.setAutomaticReconnect(true);
                        options.setCleanSession(true);
                        options.setConnectionTimeout(10);
                        mqttClient.connect(options);
                        return mqttClient;
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                    return null;
                }));

        for (int i = 0; i < Integer.MAX_VALUE; ++i) {
            cars.forEach((carModel, mqttClient) -> {
                try {
                    CarData carData = carModel.nextValue();
                    System.out.println(carData);

                    ObjectMapper om = new ObjectMapper();
                    om.registerModule(new JavaTimeModule());
                    om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                    MqttMessage msg = new MqttMessage(om.writeValueAsBytes(carData));
                    msg.setQos(1);
                    msg.setRetained(true);
                    mqttClient.publish("/mjson", msg);

                } catch (MqttException | JsonProcessingException e) {
                    e.printStackTrace();
                }
            });

            // Send a text every 10 seconds
            Thread.sleep(10000);
        }
    }
}

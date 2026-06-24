package com.epam.iotsim.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Backend entry point. Scans the whole {@code com.epam.iotsim} tree. */
@SpringBootApplication(scanBasePackages = "com.epam.iotsim")
public class IotSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotSimulatorApplication.class, args);
    }
}

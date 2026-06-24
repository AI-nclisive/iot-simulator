package com.ainclusive.iotsim.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Backend entry point. Scans the whole {@code com.ainclusive.iotsim} tree. */
@SpringBootApplication(scanBasePackages = "com.ainclusive.iotsim")
public class IotSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotSimulatorApplication.class, args);
    }
}

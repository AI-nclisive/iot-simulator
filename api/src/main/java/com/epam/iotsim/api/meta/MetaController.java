package com.epam.iotsim.api.meta;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal versioned endpoint confirming the API surface is wired. Real resource
 * controllers (projects, data-sources, ...) follow backend-specs/05_API_CONTRACT.md.
 * Path-based major versioning at /api/v1 (decision D7).
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of(
                "name", "iot-simulator",
                "apiVersion", "v1");
    }
}

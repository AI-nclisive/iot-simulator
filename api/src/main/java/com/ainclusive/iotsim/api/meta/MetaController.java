package com.ainclusive.iotsim.api.meta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Platform")
@RequestMapping("/api/v1")
public class MetaController {

    @Operation(summary = "Get API metadata",
            description = "Returns the application name and API version, confirming the versioned"
                    + " API surface is wired and reachable.")
    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of(
                "name", "iot-simulator",
                "apiVersion", "v1");
    }
}

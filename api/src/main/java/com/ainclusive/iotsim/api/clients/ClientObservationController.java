package com.ainclusive.iotsim.api.clients;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.clientobservation.ClientObservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connected-client observation per data source (IS-052): the clients currently
 * connected to a simulated source plus the full connection history. Live
 * connect/disconnect events are on the SSE stream
 * ({@code GET .../stream/clients}); this is the point-in-time query.
 * See backend-specs/05_API_CONTRACT.md.
 *
 * <p>Authorization (IS-077): read-only — {@link Permission#OBSERVE} (user + admin).
 */
@RestController
@Tag(name = "Client Observations",
        description = "Point-in-time view of clients currently connected to a simulated source,"
                + " plus the full connection history.")
public class ClientObservationController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final ClientObservationService clients;

    public ClientObservationController(ClientObservationService clients) {
        this.clients = clients;
    }

    // An unknown {id} returns 200 with empty lists rather than 404: this mirrors the
    // sibling SSE observability endpoints (/stream/{values,clients,runtime}), spec 05
    // does not require 404 here, and empty lists are an unambiguous "no clients" answer.
    @Operation(summary = "Get connected clients and history",
            description = "Returns a point-in-time snapshot of clients connected to the source plus the full"
                    + " connection history; live connect/disconnect events are on the SSE stream instead.")
    @GetMapping("/api/v1/data-sources/{id}/clients")
    @PreAuthorize(OBSERVE)
    public ClientsResponse clients(@PathVariable String id) {
        List<ClientConnectionDto> connected =
                clients.current(id).stream().map(ClientConnectionDto::from).toList();
        List<ClientConnectionDto> history =
                clients.history(id).stream().map(ClientConnectionDto::from).toList();
        return new ClientsResponse(connected, history);
    }

    /** Currently-connected clients plus the full connection log (both newest first). */
    public record ClientsResponse(List<ClientConnectionDto> connected, List<ClientConnectionDto> history) {}
}

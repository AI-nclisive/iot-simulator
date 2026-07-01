package com.ainclusive.iotsim.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * No-op {@link PermissionService} for trusted local mode (IS-077 / IS-078).
 *
 * <p>In local mode every request runs as the implicit {@code local} principal with
 * full control (SPEC "Use Product Without Login"), so all permission checks pass
 * unconditionally. This keeps the enforcement point uniform — controllers always
 * call {@code @PreAuthorize} — while making local mode a true no-op.
 *
 * <p>Active only when {@code iotsim.mode=local} (the default). Shared mode wires
 * a JWT-backed implementation from IS-076 instead.
 */
@Service("permissionService")
@ConditionalOnProperty(name = "iotsim.mode", havingValue = "local", matchIfMissing = true)
public class LocalPermissionService implements PermissionService {

    @Override
    public boolean hasPermission(Authentication authentication, Permission permission) {
        // Local mode: implicit principal has every permission — never block.
        return true;
    }
}

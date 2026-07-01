package com.ainclusive.iotsim.api.security;

import org.springframework.security.core.Authentication;

/**
 * Port: resolves whether the currently authenticated principal holds a given permission.
 *
 * <p>Two implementations are active depending on deployment mode (IS-077 / IS-078):
 * <ul>
 *   <li><b>Local mode</b>: {@link LocalPermissionService} — always returns {@code true}
 *       (the implicit {@code local} principal has full control).
 *   <li><b>Shared mode</b>: wired in IS-076, resolves permissions from JWT roles via
 *       the {@code role_permissions} table (flexible permission model, D2).
 * </ul>
 *
 * <p>Callers use this service via Spring Security method expressions, e.g.:
 * <pre>{@code
 * @PreAuthorize("@permissionService.hasPermission(authentication, T(…Permission).SOURCE_START)")
 * }</pre>
 */
public interface PermissionService {

    /**
     * Returns {@code true} when the given principal holds the requested permission.
     *
     * @param authentication the current Spring Security authentication (never null when
     *                       called from a {@code @PreAuthorize} expression inside an
     *                       authenticated context)
     * @param permission     the fine-grained capability to check
     */
    boolean hasPermission(Authentication authentication, Permission permission);
}

package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.platform.runtime.EndpointSecurity;
import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Translates the data-source endpoint-security JSON between its three shapes:
 * API-write (plaintext {@code password}), storage ({@code passwordHash}), and the
 * neutral {@link EndpointSecurity} runtime model. Hashes passwords on write and
 * redacts hashes for the API response. Passwords never leave this class in plaintext
 * once stored. See docs/superpowers/specs/2026-07-02-is-130-opcua-endpoint-security-design.md.
 */
final class EndpointSecurityCodec {

    private static final ObjectMapper JSON = new ObjectMapper();

    private EndpointSecurityCodec() {}

    static String normalizeForStorage(String rawJson) {
        if (isBlank(rawJson)) {
            return "{}";
        }
        JsonNode userTokens = parse(rawJson).path("userTokens");
        boolean anonymous = userTokens.path("anonymous").asBoolean(true);
        JsonNode username = userTokens.path("username");
        boolean usernameEnabled = username.path("enabled").asBoolean(false);
        if (!anonymous && !usernameEnabled) {
            throw new IllegalArgumentException("securityConfig: at least one user-token type must be accepted");
        }
        ArrayNode outUsers = JSON.createArrayNode();
        if (usernameEnabled) {
            JsonNode users = username.path("users");
            if (!users.isArray() || users.isEmpty()) {
                throw new IllegalArgumentException("securityConfig: username auth requires at least one user");
            }
            for (JsonNode u : users) {
                String name = u.path("username").asString("");
                String password = u.path("password").asString("");
                if (name.isBlank()) {
                    throw new IllegalArgumentException("securityConfig: user username must not be blank");
                }
                if (password.isEmpty()) {
                    throw new IllegalArgumentException("securityConfig: user password must not be blank");
                }
                ObjectNode outUser = JSON.createObjectNode();
                outUser.put("username", name);
                outUser.put("passwordHash", PasswordHash.encode(password));
                outUsers.add(outUser);
            }
        }
        return buildStored(anonymous, usernameEnabled, outUsers);
    }

    static EndpointSecurity toModel(String storedJson) {
        if (isBlank(storedJson)) {
            return EndpointSecurity.none();
        }
        JsonNode userTokens = parse(storedJson).path("userTokens");
        if (userTokens.isMissingNode()) {
            return EndpointSecurity.none();
        }
        boolean anonymous = userTokens.path("anonymous").asBoolean(true);
        JsonNode username = userTokens.path("username");
        boolean usernameEnabled = username.path("enabled").asBoolean(false);
        List<EndpointSecurity.UserCredential> users = new ArrayList<>();
        if (usernameEnabled) {
            for (JsonNode u : username.path("users")) {
                users.add(new EndpointSecurity.UserCredential(
                        u.path("username").asString(""), u.path("passwordHash").asString("")));
            }
        }
        return new EndpointSecurity(anonymous, usernameEnabled, users);
    }

    static String redact(String storedJson) {
        if (isBlank(storedJson)) {
            return "{}";
        }
        JsonNode userTokens = parse(storedJson).path("userTokens");
        if (userTokens.isMissingNode()) {
            return "{}";
        }
        boolean anonymous = userTokens.path("anonymous").asBoolean(true);
        JsonNode username = userTokens.path("username");
        boolean usernameEnabled = username.path("enabled").asBoolean(false);
        ArrayNode outUsers = JSON.createArrayNode();
        if (usernameEnabled) {
            for (JsonNode u : username.path("users")) {
                ObjectNode outUser = JSON.createObjectNode();
                outUser.put("username", u.path("username").asString(""));
                outUsers.add(outUser);
            }
        }
        return buildStored(anonymous, usernameEnabled, outUsers);
    }

    private static String buildStored(boolean anonymous, boolean usernameEnabled, ArrayNode users) {
        ObjectNode usernameNode = JSON.createObjectNode();
        usernameNode.put("enabled", usernameEnabled);
        usernameNode.set("users", users);
        ObjectNode userTokens = JSON.createObjectNode();
        userTokens.put("anonymous", anonymous);
        userTokens.set("username", usernameNode);
        ObjectNode root = JSON.createObjectNode();
        root.set("userTokens", userTokens);
        return root.toString();
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("securityConfig must be valid JSON");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "{}".equals(s.trim());
    }
}

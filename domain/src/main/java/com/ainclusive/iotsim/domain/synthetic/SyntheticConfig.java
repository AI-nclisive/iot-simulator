package com.ainclusive.iotsim.domain.synthetic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The serialized synthetic-generation setup stored in {@code DataSource.runtimeConfig}
 * — a master {@code seed} ({@code null} = run picks one) plus the per-variable
 * bindings. (backend-specs/06 "Synthetic generation model".)
 */
public record SyntheticConfig(Long seed, List<SyntheticVariableConfig> variables) {

    public SyntheticConfig {
        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException("synthetic config requires at least one variable");
        }
        variables = List.copyOf(variables);
        Set<String> ids = new HashSet<>();
        for (SyntheticVariableConfig v : variables) {
            if (!ids.add(v.nodeId())) {
                throw new IllegalArgumentException("duplicate synthetic variable nodeId: " + v.nodeId());
            }
        }
    }
}

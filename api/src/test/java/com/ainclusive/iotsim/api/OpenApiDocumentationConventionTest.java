package com.ainclusive.iotsim.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Documentation guard (Swagger UI): every {@link RestController} must carry an OpenAPI
 * {@link Tag} whose name is one of the nine domain groups, and every request-mapped handler an
 * {@link Operation} with a non-blank summary. The nine group descriptions live centrally in
 * {@link OpenApiConfig} (one authoritative summary per group); this test verifies each group a
 * controller uses is actually declared there with a non-blank description. A newly added
 * undocumented controller/endpoint, or a controller tagged with an unknown group, fails here.
 */
class OpenApiDocumentationConventionTest {

    private static final String BASE_PACKAGE = "com.ainclusive.iotsim.api";

    /** The nine Swagger groups the API is organised into. See OpenApiConfig. */
    private static final Set<String> ALLOWED_GROUPS = Set.of(
            "Platform",
            "Projects",
            "Data Sources",
            "Recordings",
            "Samples",
            "Scenarios",
            "Runs",
            "Evidence",
            "Monitoring",
            "Edit Leases");

    @Test
    void everyRestControllerAndEndpointIsDocumented() throws ClassNotFoundException {
        // Group name -> description, as declared centrally in OpenApiConfig.
        Map<String, String> declaredGroups = new LinkedHashMap<>();
        for (io.swagger.v3.oas.models.tags.Tag tag :
                new OpenApiConfig().iotSimulatorOpenApi().getTags()) {
            declaredGroups.put(tag.getName(), tag.getDescription());
        }

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(BASE_PACKAGE);

        List<String> violations = new ArrayList<>();
        Set<String> usedGroups = new TreeSet<>();
        int controllers = 0;
        for (BeanDefinition bd : candidates) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            controllers++;

            Tag tag = type.getAnnotation(Tag.class);
            if (tag == null || tag.name().isBlank()) {
                violations.add(type.getSimpleName() + ": missing class-level @Tag with a non-blank name");
            } else if (!ALLOWED_GROUPS.contains(tag.name())) {
                violations.add(type.getSimpleName() + ": @Tag name \"" + tag.name()
                        + "\" is not one of the allowed groups " + new TreeSet<>(ALLOWED_GROUPS));
            } else {
                usedGroups.add(tag.name());
            }

            for (Method method : type.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                Operation op = method.getAnnotation(Operation.class);
                if (op == null || op.summary().isBlank()) {
                    violations.add(type.getSimpleName() + "#" + method.getName()
                            + ": missing @Operation with a non-blank summary");
                }
            }
        }

        // Every group a controller uses must be declared centrally with a non-blank description.
        for (String group : usedGroups) {
            if (!declaredGroups.containsKey(group)) {
                violations.add("Group \"" + group + "\" is used by a controller but not declared in OpenApiConfig");
            } else if (declaredGroups.get(group) == null || declaredGroups.get(group).isBlank()) {
                violations.add("Group \"" + group + "\" has no description in OpenApiConfig");
            }
        }

        assertThat(controllers)
                .as("component scan of %s should find the REST controllers", BASE_PACKAGE)
                .isGreaterThanOrEqualTo(20);
        assertThat(violations)
                .as("REST controllers/endpoints missing OpenAPI documentation")
                .isEmpty();
    }
}

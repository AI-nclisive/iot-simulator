package com.ainclusive.iotsim.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Documentation guard (Swagger UI): every {@link RestController} must carry an OpenAPI
 * {@link Tag} with a non-blank description, and every request-mapped handler an
 * {@link Operation} with a non-blank summary — so the Swagger UI stays self-explanatory.
 * A newly added undocumented controller or endpoint fails the build here.
 */
class OpenApiDocumentationConventionTest {

    private static final String BASE_PACKAGE = "com.ainclusive.iotsim.api";

    @Test
    void everyRestControllerAndEndpointIsDocumented() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(BASE_PACKAGE);

        List<String> violations = new ArrayList<>();
        int controllers = 0;
        for (BeanDefinition bd : candidates) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            controllers++;

            Tag tag = type.getAnnotation(Tag.class);
            if (tag == null || tag.description().isBlank()) {
                violations.add(type.getSimpleName() + ": missing class-level @Tag with a non-blank description");
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

        assertThat(controllers)
                .as("component scan of %s should find the REST controllers", BASE_PACKAGE)
                .isGreaterThanOrEqualTo(20);
        assertThat(violations)
                .as("REST controllers/endpoints missing OpenAPI documentation")
                .isEmpty();
    }
}

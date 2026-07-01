plugins {
    id("buildlogic.java-conventions")
}

description = "Domain modules: projects, schemas, recordings, scenarios, faults, evidence, observability."

dependencies {
    api(project(":protocol-model"))
    api(project(":platform"))
    implementation(project(":persistence"))

    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    // spring-security-core: SecurityContextHolder used by PermissionService (IS-076).
    // BOM-managed version via Spring Boot platform; no version literal needed.
    implementation(libs.spring.security.core)
    // Jackson 3 (BOM-managed) — evidence manifests are JSON; assembly/export lives here
    // (api -> domain -> persistence), so the JSON handling does too.
    implementation("tools.jackson.core:jackson-databind")
    // SLF4J API (BOM-managed) — structured logging for background-thread tick failures.
    implementation("org.slf4j:slf4j-api")

    testImplementation(libs.mockito.core)
}

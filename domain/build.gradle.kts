plugins {
    id("buildlogic.java-conventions")
}

description = "Domain modules: projects, schemas, recordings, scenarios, faults, evidence, observability."

dependencies {
    api(project(":protocol-model"))
    api(project(":platform"))
    implementation(project(":persistence"))

    implementation(libs.spring.context)
    // Jackson 3 (BOM-managed) — evidence manifests are JSON; assembly/export lives here
    // (api -> domain -> persistence), so the JSON handling does too.
    implementation("tools.jackson.core:jackson-databind")
}

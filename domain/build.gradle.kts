plugins {
    id("buildlogic.java-conventions")
}

description = "Domain modules: projects, schemas, recordings, scenarios, faults, evidence, observability."

dependencies {
    api(project(":protocol-model"))
    api(project(":platform"))
    implementation(project(":persistence"))

    implementation(libs.spring.context)
}

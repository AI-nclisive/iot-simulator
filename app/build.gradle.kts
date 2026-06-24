plugins {
    id("buildlogic.java-conventions")
    alias(libs.plugins.spring.boot)
}

description = "Spring Boot bootstrap: wires api + domain + supervisor + persistence."

dependencies {
    implementation(project(":api"))
    implementation(project(":domain"))
    implementation(project(":runtime-supervisor"))
    implementation(project(":persistence"))
    implementation(project(":platform"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}

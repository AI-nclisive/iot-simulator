plugins {
    id("buildlogic.java-conventions")
}

description = "Public API: REST/OpenAPI + SSE; authentication and authorization enforcement."

dependencies {
    api(project(":domain"))
    implementation(project(":runtime-supervisor"))
    implementation(project(":platform"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.springdoc.openapi.webmvc)
}

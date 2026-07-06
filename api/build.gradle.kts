plugins {
    id("buildlogic.java-conventions")
}

description = "Public API: REST/OpenAPI + SSE; authentication and authorization enforcement."

dependencies {
    api(project(":domain"))
    implementation(project(":runtime-supervisor"))
    implementation(project(":platform"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.springdoc.openapi.webmvc)

    testImplementation(project(":persistence"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.security.test)
}

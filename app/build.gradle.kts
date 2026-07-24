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
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security)
    testImplementation(libs.spring.boot.starter.oauth2.resource.server)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // Full spawn round-trip IT connects to the spawned worker's real OPC UA
    // endpoint with a Milo client (IS-039).
    testImplementation(libs.milo.sdk.client)
}

// The full spawn IT launches the real packaged OPC UA worker as a child process
// and connects to its OPC UA endpoint. Hand the worker's installDist path and a
// pinned JAVA_HOME (the build toolchain) to the test JVM; the IT self-skips if the
// property is absent. See backend-specs/02_WORKER_CONTRACT_AND_IPC.md (IS-039).
tasks.test {
    dependsOn(":workers:worker-opcua:installDist")
    val opcuaDist = project(":workers:worker-opcua").layout.buildDirectory
        .dir("install/worker-opcua").map { it.asFile.absolutePath }
    val workerJavaHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map { it.metadata.installationPath.asFile.absolutePath }
    doFirst {
        systemProperty("iotsim.worker.opcua.dist", opcuaDist.get())
        environment("JAVA_HOME", workerJavaHome.get())
    }
}

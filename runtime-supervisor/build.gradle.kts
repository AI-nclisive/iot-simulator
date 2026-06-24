plugins {
    id("buildlogic.java-conventions")
}

description = "Runtime supervisor: worker lifecycle, IPC client, health, ports, governance. Protocol-agnostic."

dependencies {
    api(project(":worker-contract"))
    api(project(":platform"))

    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.stub)
    runtimeOnly(libs.grpc.netty.shaded)

    // Tests stand up a real loopback worker server to exercise the client.
    testImplementation(platform(libs.grpc.bom))
    testImplementation(libs.grpc.netty.shaded)
}

// The spawn IT launches the real packaged OPC UA worker as a child process, so it
// needs the worker's installDist output. The path and a pinned JAVA_HOME (the
// build toolchain) are handed to the test JVM; the IT self-skips if the property
// is absent. See backend-specs/02_WORKER_CONTRACT_AND_IPC.md (IS-039).
tasks.test {
    dependsOn(":workers:worker-opcua:installDist")
    val opcuaDist = project(":workers:worker-opcua").layout.buildDirectory
        .dir("install/worker-opcua").map { it.asFile.absolutePath }
    val workerJavaHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }.map { it.metadata.installationPath.asFile.absolutePath }
    doFirst {
        systemProperty("iotsim.worker.opcua.dist", opcuaDist.get())
        environment("JAVA_HOME", workerJavaHome.get())
    }
}

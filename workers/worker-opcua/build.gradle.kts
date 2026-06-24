plugins {
    id("buildlogic.java-conventions")
    application
}

description = "OPC UA protocol worker (Eclipse Milo). Lean JVM, no Spring."

dependencies {
    implementation(project(":worker-contract"))

    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty.shaded)

    implementation(libs.milo.sdk.server)
    // Milo 0.6 needs javax.xml.bind (JAXB) at runtime on Java 11+.
    runtimeOnly(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)

    testImplementation(libs.milo.sdk.client)
}

application {
    mainClass = "com.ainclusive.iotsim.worker.opcua.OpcUaWorkerMain"
}

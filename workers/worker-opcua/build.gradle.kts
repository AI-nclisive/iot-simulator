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
    // Client SDK powers create-from-scan discovery: the worker acts as an OPC UA
    // client against a real source (IS-043). Already in the version catalog.
    implementation(libs.milo.sdk.client)
    // Milo 0.6 needs javax.xml.bind (JAXB) at runtime on Java 11+.
    runtimeOnly(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)
}

application {
    mainClass = "com.ainclusive.iotsim.worker.opcua.OpcUaWorkerMain"
}

plugins {
    id("buildlogic.java-conventions")
    application
}

description = "Modbus TCP protocol worker (j2mod). Lean JVM, no Spring."

dependencies {
    implementation(project(":worker-contract"))

    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.stub)
    runtimeOnly(libs.grpc.netty.shaded)

    implementation(libs.j2mod)
}

application {
    mainClass = "com.ainclusive.iotsim.worker.modbus.ModbusWorkerMain"
}

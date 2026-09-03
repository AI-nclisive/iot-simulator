plugins {
    id("buildlogic.java-conventions")
    application
}

description = "Modbus TCP/RTU protocol worker. Lean JVM, no Spring."

dependencies {
    implementation(project(":worker-contract"))

    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty.shaded)

    implementation(libs.j2mod)
    implementation(libs.jserialcomm)
}

application {
    mainClass = "com.ainclusive.iotsim.worker.modbus.ModbusWorkerMain"
}

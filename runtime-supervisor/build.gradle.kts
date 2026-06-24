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

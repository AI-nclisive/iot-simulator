import com.google.protobuf.gradle.id

plugins {
    id("buildlogic.java-conventions")
    alias(libs.plugins.protobuf)
}

description = "ProtocolDataSource contract + supervisor⇄worker gRPC IPC (backend-specs/02)."

dependencies {
    api(project(":protocol-model"))

    api(platform(libs.grpc.bom))
    api(libs.protobuf.java)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)

    // javax.annotation.Generated referenced by generated gRPC stubs.
    compileOnly(libs.tomcat.annotations)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        id("grpc") {
            artifact = libs.grpc.protoc.gen.get().toString()
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

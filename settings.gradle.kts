pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "iot-simulator"

// Module map per backend-specs/07_MODULE_STRUCTURE.md.
// Dependencies flow downward only; workers depend on nothing but worker-contract.
include(
    "protocol-model",
    "worker-contract",
    "platform",
    "persistence",
    "domain",
    "runtime-supervisor",
    "api",
    "app",
    "workers:worker-opcua",
    "workers:worker-modbus",
)

plugins {
    id("buildlogic.java-conventions")
}

description = "Cross-cutting ports: object storage, clock, id generation, secrets."

dependencies {
    api(project(":protocol-model"))
}

// Root build. Per-module configuration lives in each module's build.gradle.kts
// and the shared buildlogic.java-conventions plugin (build-logic/).
plugins {
    base
    // Loaded once here (apply false) so the convention plugin can apply Spotless
    // to every module from a single classloader (shared build service).
    id("com.diffplug.spotless") version "8.8.0" apply false
}

description = "IoT Data Source Simulator — modular-monolith backend"

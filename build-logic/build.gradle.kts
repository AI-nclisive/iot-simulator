plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // compileOnly: gives the convention plugin the SpotlessExtension type without
    // exporting Spotless to each module's classpath. The plugin itself is loaded
    // once in the root build (plugins{} apply false) to avoid a classloader clash
    // of Spotless's shared build service across modules.
    compileOnly("com.diffplug.spotless:spotless-plugin-gradle:8.8.0")
}

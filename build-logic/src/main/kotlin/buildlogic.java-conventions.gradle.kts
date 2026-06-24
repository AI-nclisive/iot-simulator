import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

// Shared Java conventions for every backend module.
// Toolchain = Java 25 LTS (backend-specs/07_MODULE_STRUCTURE.md).
plugins {
    `java-library`
    checkstyle
    jacoco
}

// Spotless is applied programmatically (external plugin in a precompiled-script
// plugins{} block breaks accessor generation).
apply(plugin = "com.diffplug.spotless")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot BOM gives consistent managed versions across all modules
    // (jOOQ, Flyway, PostgreSQL, JUnit, AssertJ, Testcontainers, ...).
    // Keep in sync with gradle/libs.versions.toml -> springBoot.
    "implementation"(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// Formatting hygiene (not a full reformatter); generated code under build/ is excluded.
configure<SpotlessExtension> {
    java {
        target("src/**/*.java")
        importOrder()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "13.6.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

tasks.withType<Checkstyle>().configureEach {
    // Skip generated sources (jOOQ, protobuf/gRPC).
    exclude("**/persistence/jooq/**", "**/workercontract/v1/**")
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

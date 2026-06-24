plugins {
    id("buildlogic.java-conventions")
}

description = "Relational persistence: Flyway migrations, jOOQ (typed SQL), repositories."

val jooqCodegen by configurations.creating

dependencies {
    api(project(":protocol-model"))
    api(project(":platform"))

    // Boot starters bring jOOQ/JDBC/Flyway plus their Spring Boot autoconfiguration
    // modules (DataSource, DSLContext, Flyway beans) — required on Spring Boot 4.
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.context)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Code-generation classpath (DDLDatabase reads migration SQL — no DB needed).
    jooqCodegen(libs.jooq.codegen)
    jooqCodegen(libs.jooq.meta.extensions)

    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testRuntimeOnly(libs.postgresql)
}

val generatedJooqDir = layout.buildDirectory.dir("generated/jooq")

val generateJooq = tasks.register<JavaExec>("generateJooq") {
    group = "build"
    description = "Generates jOOQ classes from Flyway migration SQL (DDLDatabase, no DB)."
    classpath = jooqCodegen
    mainClass = "org.jooq.codegen.GenerationTool"
    args(layout.projectDirectory.file("jooq-codegen.xml").asFile.absolutePath)
    inputs.dir(layout.projectDirectory.dir("src/main/resources/db/migration"))
    inputs.file(layout.projectDirectory.file("jooq-codegen.xml"))
    outputs.dir(generatedJooqDir)
}

sourceSets["main"].java.srcDir(generatedJooqDir)

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateJooq)
}

plugins {
    java
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "de.leos.limbo"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Minestom — reine Library (kein Server-Fork), wir schreiben die Server-Logik selbst.
    implementation("net.minestom:minestom:2026.05.17-1.21.11")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

application {
    mainClass.set("de.leos.limbo.Main")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("limbo-server.jar")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

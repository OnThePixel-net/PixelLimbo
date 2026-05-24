plugins {
    java
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.onthepixel.limbo"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
    maven("https://maven.lenni0451.net/everything")
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://libraries.minecraft.net")
    maven("https://jitpack.io")
}

dependencies {
    // Minestom — minimal Minecraft server library
    implementation("net.minestom:minestom:2026.05.17-1.21.11")

    // ViaProxy as a library — handles the multi-version protocol
    // translation in front of Minestom inside the same JVM.
    implementation("net.raphimc:ViaProxy:3.4.11")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
application { mainClass.set("net.onthepixel.limbo.Main") }
tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("pixellimo.jar")
    mergeServiceFiles()
}
tasks.build { dependsOn(tasks.shadowJar) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

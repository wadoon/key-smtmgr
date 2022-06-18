import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "org.keyproject.smtmgr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    implementation("com.vdurmont:semver4j:3.1.0")
    implementation("org.rauschig:jarchivelib:1.2.0")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta7")
    //implementation("org.asynchttpclient:async-http-client:2.12.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}
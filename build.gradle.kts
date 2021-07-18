plugins {
    kotlin("jvm") version "1.5.20"
    id("idea")
    java
    jacoco
    id("application")
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
}

group = "com.idiomcentric"

application {
    mainClass.set("com.idiomcentric.MainKt")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

object V {
    const val curator = "5.1.0"
    const val logbackClassic = "1.2.3"
}

dependencies {
    implementation("org.apache.curator:apache-curator:${V.curator}")
    implementation("org.apache.curator:curator-x-async:${V.curator}")
    implementation("org.apache.curator:curator-framework:${V.curator}")
    implementation("org.apache.curator:curator-recipes:${V.curator}")

    implementation("ch.qos.logback:logback-classic:${V.logbackClassic}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.1")
}

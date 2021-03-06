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
    const val logback = "0.1.5"
    const val logbackClassic = "1.2.3"
    const val jackson = "2.12.4"
    const val coroutines = "1.5.1"
    const val kotlinLoggingJvm = "2.0.10"
    const val testContainers = "1.15.3"
}

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:${V.kotlinLoggingJvm}")
    implementation("ch.qos.logback:logback-classic:${V.logbackClassic}")
    implementation("ch.qos.logback.contrib:logback-json-classic:${V.logback}")
    implementation("ch.qos.logback.contrib:logback-jackson:${V.logback}")

    implementation("org.apache.curator:apache-curator:${V.curator}")
    implementation("org.apache.curator:curator-x-async:${V.curator}")
    implementation("org.apache.curator:curator-framework:${V.curator}")
    implementation("org.apache.curator:curator-recipes:${V.curator}")

    implementation("ch.qos.logback:logback-classic:${V.logbackClassic}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${V.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${V.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${V.coroutines}")

    implementation("com.fasterxml.jackson.core:jackson-databind:${V.jackson}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${V.jackson}")

    testImplementation("org.testcontainers:testcontainers:${V.testContainers}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showStackTraces = true
        }
    }
}

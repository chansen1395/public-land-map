plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("io.ktor.plugin") version "2.3.12"
}

group = "com.connorhansen.publiclandmap"
version = "1.0.0"

application {
    mainClass.set("com.connorhansen.publiclandmap.api.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val kotlinxSerializationVersion = "1.6.3"
val hikariVersion = "5.1.0"
val postgresVersion = "42.7.3"
val logbackVersion = "1.4.14"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-caching-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.24")
}

tasks.test {
    useJUnit()
}

ktor {
    fatJar {
        archiveFileName.set("public-land-map-api.jar")
    }
}

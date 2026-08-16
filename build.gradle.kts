plugins {
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
}

group = "com.juiceplan"
version = "0.0.1-SNAPSHOT"

// This sandboxed Linux environment's Gradle daemon needs the legacy VFORK process-launch
// mechanism for its own JVM to successfully fork test-worker processes; VFORK is not a
// supported launch mechanism on macOS/BSD JDKs, so this must not apply there. It must be set
// as a JVM system property on the process doing the forking (the Gradle daemon/build JVM
// itself, not the forked test-worker JVM), and early enough (build-script configuration time)
// to run before that JVM's `java.lang.ProcessImpl` class is first loaded/initialized.
if (System.getProperty("os.name").lowercase().contains("linux")) {
    System.setProperty("jdk.lang.Process.launchMechanism", "VFORK")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

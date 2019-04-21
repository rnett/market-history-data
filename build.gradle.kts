import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript{
    repositories {
        mavenCentral()
        maven("https://kotlin.bintray.com/kotlinx")
        jcenter()
    }
    dependencies{
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.3.11")
    }
}

plugins {
    kotlin("jvm") version "1.3.21"
    `maven-publish`
}

apply{
    plugin("kotlinx-serialization")
}

group = "com.rnett.market-history-data"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://kotlin.bintray.com/kotlinx")
    jcenter()
}

val ktor_version = "1.1.4"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    //api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.1.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.9.1")

    api("com.github.salomonbrys.kotson:kotson:2.5.0")
    api("com.github.rnett:core:1.5.0")

    implementation("com.github.rnett:exposedgson:1.1.0")

    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-apache:$ktor_version")

    implementation("com.github.rnett:launchpad:1.2.2")
    implementation("com.github.rnett:sde:6af45d81bc")


    api("org.jetbrains.exposed:exposed:0.13.6")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(kotlin.sourceSets["main"].kotlin)
}

publishing {
    publications {
        create("default", MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar)
        }
        create("mavenJava", MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            url = uri("$buildDir/repository")
        }
    }
}
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// The Java tasks have to target the same release as Kotlin, or the build refuses to run.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// A JVM-only module. Used where the platform API is the whole point — `SSLEngine`, for one —
// and a multiplatform wrapper would only add a layer with nothing on the other side.
kotlin {
    explicitApi()
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    "testImplementation"(kotlin("test"))
}

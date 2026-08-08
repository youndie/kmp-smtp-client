import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    // A library: every public declaration spells out its visibility and its return type.
    explicitApi()

    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Target platform number one; milestones are closed against it.
    linuxX64()

    // The host target for the local TDD loop: linuxX64 tests do not run on macOS.
    // See docs/research/research-architecture.md, D9.
    macosArm64()

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

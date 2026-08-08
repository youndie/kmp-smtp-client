import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("smtp.publish")
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

    // Server platforms next in line. They compile in every gate; their tests run only on a
    // matching host, which nobody here has — see the module documents for what that means.
    linuxArm64()
    macosX64()
    mingwX64()

    // Apple mobile, compile only. The protocol layer is pure Kotlin and works there, but there is
    // no TLS provider for it yet (M-83), and the simulator targets are left out on purpose: their
    // test tasks need an iOS SDK that is not installed here, and a test task that cannot run is
    // worse than an absent target — it looks like coverage.
    iosArm64()

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

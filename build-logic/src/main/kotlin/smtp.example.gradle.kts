import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// An example, not a library: it is never published, and it exists so that the quick start cannot
// rot unnoticed — every build compiles it.
//
// The Kotlin plugin is applied through a convention rather than through the version catalog: the
// two arrive on different classloaders, and mixing them fails at configuration time.
kotlin {
    linuxX64()
    macosArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "main"
        }
    }
}

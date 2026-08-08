import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    // Библиотека: видимость каждого публичного объявления пишется руками, тип возврата тоже.
    explicitApi()

    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Целевая платформа номер один; по ней закрываются вехи.
    linuxX64()

    // Хостовый таргет для локального цикла TDD: тесты linuxX64 на macOS не запускаются.
    // См. docs/research/research-architecture.md, Р9.
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

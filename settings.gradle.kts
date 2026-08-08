pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "kmp-smtp-client"

include(":smtp-core")
include(":smtp-client")
include(":smtp-sasl")
include(":smtp-testing")
include(":smtp-tls-jvm")
include(":smtp-tls-openssl")
include(":smtp-transport-ktor")

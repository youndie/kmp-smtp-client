plugins {
    base
}

// ktlint is wired in as a CLI tool rather than through a wrapper plugin: this project wants
// exactly version 1.8.0 and exactly its behaviour. Rationale — docs/research/research-architecture.md, D10.
val ktlint: Configuration by configurations.creating

dependencies {
    // The `-all.jar`, requested through artifact-only notation (`:all@jar`): ktlint-cli publishes
    // two variants in its Gradle metadata, and resolving the plain one turns into a fight with the
    // Bundling/Usage attributes — first clikt goes missing (it is runtime-scoped), then
    // kotlin-stdlib (it has KMP variants of its own). `@jar` ignores the metadata and fetches
    // exactly the jar that ships as the CLI.
    ktlint("${libs.ktlint.cli.get().module}:${libs.versions.ktlint.get()}:all@jar")
}

private val ktlintTargets =
    listOf(
        "**/src/**/*.kt",
        "**/*.kts",
        "!build-logic/build/**",
    )

val ktlintCheck by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks the code style with ktlint 1.8.0 as configured in .editorconfig"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = ktlintTargets + listOf("--relative")
}

val ktlintFormat by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Applies the fixes ktlint can make on its own"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = ktlintTargets + listOf("--relative", "--format")
}

tasks.check {
    dependsOn(ktlintCheck)
}

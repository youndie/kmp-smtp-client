plugins {
    base
}

// ktlint подключён как CLI-инструмент, а не плагином-обёрткой: нужна ровно версия 1.8.0 и ровно
// её поведение. Обоснование — docs/research/research-architecture.md, Р10.
val ktlint: Configuration by configurations.creating

dependencies {
    // Именно `-all.jar` и именно артефактной нотацией (`:all@jar`): у ktlint-cli два варианта в
    // Gradle-метаданных, и разрешение обычного превращается в возню с атрибутами Bundling/Usage —
    // сперва теряется clikt (он runtime-scope), потом kotlin-stdlib (у него свои KMP-варианты).
    // `@jar` метаданные игнорирует: приезжает ровно тот jar, который распространяется как CLI.
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
    description = "Проверяет стиль ktlint 1.8.0 по .editorconfig"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = ktlintTargets + listOf("--relative")
}

val ktlintFormat by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Исправляет то, что ktlint умеет исправлять сам"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = ktlintTargets + listOf("--relative", "--format")
}

tasks.check {
    dependsOn(ktlintCheck)
}

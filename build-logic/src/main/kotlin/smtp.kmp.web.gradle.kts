plugins {
    id("smtp.kmp")
}

// Adds the JavaScript targets to a module that is pure Kotlin.
//
// Node only, never a browser: a browser has no TCP socket at all, so a browser artefact could not
// work even in principle (docs/research/research-architecture.md, decision D5). Modules that touch
// the network do not get these targets until there is a Node transport (M-84).
kotlin {
    js(IR) { nodejs() }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
}

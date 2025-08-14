// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
dependencies {
    // ...existing code...
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}

tasks.register("runAllTests") {
    description = "Runs all tests in all submodules"
    group = "verification"

    dependsOn(
        subprojects.flatMap { subproject ->
            subproject.tasks.matching { it.name == "test" }
        }
    )
}

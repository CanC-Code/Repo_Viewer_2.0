// Top-level build file where you can add configuration options common to all sub-projects/modules.

@Suppress("DSL_SCOPE_VIOLATION") // Prevents IDE inspection errors when using Version Catalogs at the root
plugins {
    // The 'apply false' command is critical here. 
    // It loads the exact version (8.4.1) from your libs.versions.toml into the global classpath 
    // WITHOUT applying the plugin to the root project itself.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Clean up task for the project root
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Use alias from libs.versions.toml for consistency
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // DO NOT APPLY COMPOSE PLUGIN HERE
}

// Remove the entire buildscript block - it's redundant with the plugins {} block above
// buildscript { ... }

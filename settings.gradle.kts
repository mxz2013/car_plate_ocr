pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // Good practice to include for plugins
    }
}

dependencyResolutionManagement {
    // Fail if project repositories are declared (enforces central declaration)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()      // Needed for Android artifacts
        mavenCentral() // Needed for many common libraries
        // Add other repositories if needed (e.g., JitPack)
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PlateOCR" // Or your actual project name
include(":app") // Include your app module (or other modules)

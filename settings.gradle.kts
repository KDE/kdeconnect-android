pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        /* Needed for org.apache.sshd debugging
        maven {
            url = uri("https://jitpack.io")
        }
        */
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }
}
rootProject.name = "kdeconnect-android"

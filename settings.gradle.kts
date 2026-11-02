pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "smallwright"

include(":controller", ":worker", ":protocol")

if (providers.gradleProperty("includeSecurityProbe").orNull == "true") {
    include(":security-probe")
}

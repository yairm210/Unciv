import java.io.File

pluginManagement {
    val gdxSourcePath = providers.gradleProperty("gdxTeaVMSource").orNull
        ?: throw GradleException("Pass -PgdxTeaVMSource=<pinned gdx-teavm source tree>")
    val gdxSource = File(gdxSourcePath)
    if (!gdxSource.isAbsolute || !gdxSource.isDirectory) {
        throw GradleException("-PgdxTeaVMSource must be an absolute source tree: $gdxSourcePath")
    }
    val gdxPlugin = File(gdxSourcePath, "tools/gdx-teavm-plugin")
    if (!File(gdxPlugin, "settings.gradle.kts").isFile) {
        throw GradleException("Pinned gdx-teavm plugin source is missing: $gdxPlugin")
    }
    includeBuild(gdxPlugin.absolutePath)
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

val gdxSourcePath = providers.gradleProperty("gdxTeaVMSource").orNull
    ?: throw GradleException("Pass -PgdxTeaVMSource=<pinned gdx-teavm source tree>")
val gdxSource = File(gdxSourcePath)
if (!gdxSource.isAbsolute || !gdxSource.isDirectory) {
    throw GradleException("-PgdxTeaVMSource must be an absolute source tree: $gdxSourcePath")
}

val teavmSourcePath = providers.gradleProperty("teavmPath").orNull
    ?: throw GradleException("Pass -PteavmPath=<pinned TeaVM source tree>")
val teavmSource = File(teavmSourcePath)
if (!teavmSource.isAbsolute || !teavmSource.isDirectory) {
    throw GradleException("-PteavmPath must be an absolute TeaVM source tree: $teavmSourcePath")
}

includeBuild(gdxSource.absolutePath) {
    dependencySubstitution {
        substitute(module("com.github.xpenatan.gdx-teavm:backend-web"))
            .using(project(":backends:backend-web"))
    }
}

includeBuild(teavmSource.absolutePath) {
    dependencySubstitution {
        substitute(module("org.teavm:teavm-gradle-plugin")).using(project(":tools:gradle"))
        substitute(module("org.teavm:teavm-extension-spi")).using(project(":extension:spi"))
        substitute(module("org.teavm:teavm-extension-annotation-processor")).using(project(":extension:processor"))
        substitute(module("org.teavm:teavm-tooling")).using(project(":tools:core"))
        substitute(module("org.teavm:teavm-core")).using(project(":core"))
        substitute(module("org.teavm:teavm-classlib")).using(project(":classlib"))
        substitute(module("org.teavm:teavm-jso")).using(project(":jso:core"))
        substitute(module("org.teavm:teavm-jso-apis")).using(project(":jso:apis"))
        substitute(module("org.teavm:teavm-jso-impl")).using(project(":jso:impl"))
        substitute(module("org.teavm:teavm-interop")).using(project(":interop:core"))
        substitute(module("org.teavm:teavm-metaprogramming-api")).using(project(":metaprogramming:api"))
        substitute(module("org.teavm:teavm-metaprogramming-impl")).using(project(":metaprogramming:impl"))
    }
}

rootProject.name = "unciv-fetch-engine-validation"

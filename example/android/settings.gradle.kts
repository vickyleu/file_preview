@file:Suppress("UnstableApiUsage")

import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists


pluginManagement {
    repositories {
        all {
            if (this is MavenArtifactRepository) {
                val url = url.toString()
                if ((url.startsWith("https://plugins.gradle.org")) || (url.startsWith("https://repo.gradle.org"))) {
                    remove(this)
                }
            }
        }
        maven {
            url = uri("https://repo.nju.edu.cn/repository/maven-public/")
        }
        maven {
            url = uri("https://mirrors.cloud.tencent.com/gradle/")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public/")
            isAllowInsecureProtocol = true
        }
    }
    val agpVersion: String by settings
    val kotlinVersion: String by settings
    val flutterVersion: String by settings
    var sdkPathExists = false
    fun flutterSdkPath(): String? {
        val properties = java.util.Properties()
        file("local.properties").inputStream().use { properties.load(it) }
        val flutterSdkPath = properties.getProperty("flutter.sdk")
        assert(flutterSdkPath != null) {
            "flutter.sdk not set in local.properties"
        }
        return flutterSdkPath
    }
    flutterSdkPath()?.apply {
        sdkPathExists = true
        settings.extra["flutterSdkPath"] = this
        includeBuild("${this}/packages/flutter_tools/gradle")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "flutter") {
                useModule("dev.flutter:flutter-gradle-plugin:1.0.0")
            }
        }
    }
    plugins {
        id("com.android.application") version (agpVersion) apply (false)
        id("com.android.library") version (agpVersion) apply (false)
        kotlin("android") version (kotlinVersion) apply (false)
        kotlin("kapt") version (kotlinVersion) apply (false)
        if (sdkPathExists) {
            id("dev.flutter.flutter-gradle-plugin") version ("1.0.0") apply false
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver") version "0.7.0"
}
toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        all {
            if (this is MavenArtifactRepository) {
                val url = url.toString()
                if ((url.startsWith("https://plugins.gradle.org")) || (url.startsWith("https://repo.gradle.org"))) {
                    remove(this)
                }
            }
        }
        maven {
            url = uri("https://repo.nju.edu.cn/repository/maven-public/")
        }
        maven {
            url = uri("https://mirrors.cloud.tencent.com/flutter/download.flutter.io/")
        }
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
        maven {
            url = uri("https://repo.nju.edu.cn/repository/maven-public/")
        }
        maven {
            url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        }

    }
}
gradle.beforeProject {
    if (this.hasProperty("target-platform")) {
        this.setProperty(
            "target-platform",
            "android-arm,android-arm64"
        )//,android-arm64  //flutter打包记得开启,flutter engine 动态构建属性,在纯Android模式下会报错
    }
}



rootProject.name = "android"
include(":app")

// Load and include Flutter plugins.
val flutterProjectRoot: Path = rootProject.projectDir.parentFile.toPath()
val pluginsFile: Path = flutterProjectRoot.resolve(".flutter-plugins-dependencies")
if (!Files.exists(pluginsFile)) {
    throw GradleException("Flutter plugins file not found. Define plugins in $pluginsFile.")
}
@Suppress("UNCHECKED_CAST")
val map = JsonSlurper().parseText(
    pluginsFile.toFile().readText()
) as? Map<String, Map<String, List<Map<String, Any>>>>

val android = (map?.get("plugins")?.get("android") ?: arrayListOf())
for (it in android) {
    val name = it["name"] as String
    val path = it["path"] as String
    val needsBuild: Boolean = it["native_build"].let { it?.toString()?.toBooleanStrict() ?: true }
    if (!needsBuild) {
        continue
    }
    val pluginDirectory = file(path).toPath().resolve("android")
    assert(pluginDirectory.exists())
    include(":$name")
    project(":$name").projectDir = pluginDirectory.toFile()
}


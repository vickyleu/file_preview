// Top-levelsbuild file where you can add configuration options common to all sub-projects/modules.
import groovy.xml.XmlSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths
import java.util.Properties

rootProject.layout.buildDirectory.set(file("../build"))

plugins {
    idea
    id("com.android.application") apply (false)
    id("com.android.library") apply (false)
    kotlin("android") apply (false)
    kotlin("kapt") apply (false)
    id("dev.flutter.flutter-gradle-plugin") apply (false)//添加flutter插件依赖,但是不应用依赖,类似与import
}

allprojects {
    if (this.hasProperty("test")) {
        this.property("test")?.apply {
            this.closureOf<Test> {
                exclude("**/*")
            }
        }
    }
}
allprojects {
    gradle.projectsEvaluated {
        tasks.withType<JavaCompile> {
            this.options.forkOptions.memoryMaximumSize = "8096m"
        }
        tasks.withType<KotlinCompile> {
            this.compilerOptions.freeCompilerArgs.addAll(
                listOf(
                    "-Xjsr305=strict",//严格模式
                    "-opt-in=kotlin.RequiresOptIn",

                    )
            )
        }
    }
}

subprojects {
    val kotlinVersion: String by rootProject.properties
    val javaVersion: String by rootProject.properties
    val androidApiVersion: String by rootProject.properties
    val androidCompileSdkInt = androidApiVersion.toInt()
//    val androidCompileSdkInt = FlutterExtension.getCompileSdkVersion()
    val androidMinSdkInt = FlutterExtension.getMinSdkVersion()
    val androidMinSdkMinimal = 21

    this.layout.buildDirectory.set(file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/${project.name}"))
    this.ext["kotlin_version"] = kotlinVersion
    this.afterEvaluate {
        val java = JavaVersion.valueOf("VERSION_$javaVersion")

        if (this.hasProperty("android") && this.name != "gradle") {
            val androidProperty = this.property("android")
            if (androidProperty is com.android.build.gradle.LibraryExtension) {
                if (androidProperty.namespace == null) {
                    androidProperty.sourceSets.getByName("main").manifest.srcFile.also {
                        val manifest = XmlSlurper().parse(file(it))
                        val packageName = manifest.getProperty("@package").toString()
                        androidProperty.namespace = packageName
                    }
                }
                androidProperty.buildFeatures.apply {
                    buildConfig = true
                }
                androidProperty.compileSdk = androidCompileSdkInt
                var current = (androidProperty.defaultConfig.minSdk ?: androidMinSdkInt)
                if (current < androidMinSdkMinimal) current = androidMinSdkMinimal
                if (current < androidMinSdkInt) current = androidMinSdkInt
                androidProperty.defaultConfig.minSdk = current

                androidProperty.defaultConfig.targetSdk = FlutterExtension.getTargetSdkVersion()

                androidProperty.compileOptions.sourceCompatibility = java
                androidProperty.compileOptions.targetCompatibility = java
                androidProperty.ndkVersion = FlutterExtension.getNdkVersion()

                androidProperty.lint {
                    abortOnError = false
                    warningsAsErrors = false
                    baseline =
                        file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/lint-baseline.xml")
                    disable += arrayListOf(
                        "MissingTranslation",
                        "KotlinNullnessAnnotation",
                        "MissingClass",
                        "UnusedResources",
                        "UnusedAttribute",
                        "UnusedIds",
                        "UnusedResourcesConfiguration"
                    )
                }
            } else if (androidProperty is com.android.build.gradle.TestedExtension) {
                if (androidProperty.namespace == null) {
                    androidProperty.sourceSets.getByName("main").manifest.srcFile.also {
                        val manifest = XmlSlurper().parse(file(it))
                        val packageName = manifest.getProperty("@package").toString()
                        println("Setting $packageName as android namespace")
                        androidProperty.namespace = packageName
                    }
                }
                androidProperty.buildFeatures.apply {
                    buildConfig = true
                }
                androidProperty.compileSdkVersion(androidCompileSdkInt)
                var current = (androidProperty.defaultConfig.minSdk ?: androidMinSdkInt)
                if (current < androidMinSdkMinimal) current = androidMinSdkMinimal
                if (current < androidMinSdkInt) current = androidMinSdkInt
                androidProperty.defaultConfig.minSdk = current

                androidProperty.defaultConfig.targetSdk = FlutterExtension.getTargetSdkVersion()
                androidProperty.ndkVersion = FlutterExtension.getNdkVersion()

                androidProperty.compileOptions.sourceCompatibility = java
                androidProperty.compileOptions.targetCompatibility = java
            }
        }


        if (this.hasProperty("kotlin") && this.name != "gradle") {
            val kotlinProperty = this.property("kotlin")
            if (kotlinProperty is org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension) {
                kotlinProperty.jvmToolchain(javaVersion.toInt())
                kotlinProperty.compilerOptions.jvmTarget.set(JvmTarget.valueOf("JVM_$javaVersion"))
                kotlinProperty.compilerOptions.suppressWarnings = true
            }
        }
        if (this.hasProperty("java") && this.name != "gradle") {
            val javaProperty = this.property("java")
            if (javaProperty is org.gradle.api.plugins.internal.DefaultJavaPluginExtension) {
                javaProperty.toolchain.languageVersion = JavaLanguageVersion.of(javaVersion.toInt())
                javaProperty.apply {
                    this.sourceCompatibility = java
                    this.targetCompatibility = java
                }
            }
        }
    }
}

subprojects {
    val kotlinVersion: String by rootProject.properties
    this.configurations.configureEach {
        com.android.tools.analytics.AnalyticsSettings.optedIn = false
        resolutionStrategy.eachDependency {
            if (this.requested.group == "org.jetbrains.kotlin") {
                if (this.requested.name != "kotlin-reflect") {
                    useVersion(kotlinVersion)
                }
            } else if (this.requested.group == "io.flutter") {
                try {
                    val engineVersion = latestFlutterVersion()
                    useVersion(engineVersion)
                } catch (ignore: Exception) {
                    println("${ignore.message}}")
                }
            }
        }

    }

    try {
        this.evaluationDependsOn(":app")
    } catch (ignore: Throwable) {
    }
}


tasks.withType<Test> {
    enabled = false
}

tasks {
    task<Delete>("clean") {
        delete(rootProject.layout.buildDirectory.get().asFile)
    }
}


tasks.withType<KotlinCompile>().configureEach {
    val javaVersion: String by properties
    kotlinOptions {
        jvmTarget = javaVersion
    }
}


idea.module {
    excludeDirs.add(file(rootProject.layout.buildDirectory.get().asFile.absolutePath))
}

fun Project.latestFlutterVersion(): String {
    val localProperties = Properties()
    val localPropertiesFile: File = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.reader(Charsets.UTF_8).use { reader ->
            localProperties.load(reader)
        }
    }
    val flutterRootPath = localProperties.getProperty("flutter.sdk")
        ?: throw GradleException("Flutter SDK not found. Define location with flutter.sdk in the local.properties file.")
    val flutterRoot = project.file(flutterRootPath)
    if (!flutterRoot.isDirectory) {
        throw GradleException("flutter.sdk must point to the Flutter SDK directory")
    }
    fun useLocalEngine(): Boolean {
        return project.hasProperty("local-engine-repo")
    }
    return if (useLocalEngine())
        "+" // Match any version since there's only one.
    else "1.0.0-" + Paths.get(flutterRoot.absolutePath, "bin", "internal", "engine.version")
        .toFile().readText().trim()
}
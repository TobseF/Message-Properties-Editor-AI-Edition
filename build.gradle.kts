// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "github.tobsef.resource.bundle.editor.ai"
version = "2026.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

buildscript {
    dependencies {
        classpath("org.commonmark:commonmark:0.22.0")// Markdown for changelog
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("261.22158.182")
        bundledPlugin("com.intellij.properties")

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

java.sourceSets["main"].java {
    srcDir("src")
}

java.sourceSets["main"].resources {
    srcDir("resources")
}

java.sourceSets["test"].java {
    srcDir("test")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "263.*"
        }


        changeNotes.set(provider {
            file("${rootDir}/CHANGELOG.md").readText().renderMarkdown()
        })
    }

    pluginVerification {
        ides {
//            ("261.22158.182").split(",")
            recommended()
        }

        // 编译时告警报错级别
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
//                FailureLevel.OVERRIDE_ONLY_API_USAGES,
//                FailureLevel.NON_EXTENDABLE_API_USAGES,
//                FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            )
        )
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

// https://github.com/commonmark/commonmark-java
fun String.renderMarkdown(): String {
    val parser = Parser.builder().build()
    val document = parser.parse(this)
    val renderer = HtmlRenderer.builder().build()
    return renderer.render(document).trim()
}
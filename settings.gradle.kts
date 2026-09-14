/*
 * 2026 Simeon L.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") {
            name = "fabric"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "neoforged"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Loom and ModDevGradle declare their own (Minecraft-specific) repositories per project; those
    // projects use only theirs, every other module resolves against the list below.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        maven("https://maven.fabricmc.net/") {
            name = "fabric"
        }
        maven("https://libraries.minecraft.net") {
            name = "mojang"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "neoforged"
        }
        maven("https://thedarkcolour.github.io/KotlinForForge/") {
            name = "kotlinforforge"
        }
    }
}

rootProject.name = "Polyglot"

include(
    ":core",
    ":platform-cli",
    ":platform-jda",
    ":platform-jda-ktx",
    ":platform-paper-common",
    ":platform-paper-legacy",
    ":platform-paper-modern",
    ":platform-brigadier",
    ":platform-minecraft-common",
    ":platform-fabric-legacy",
    ":platform-fabric-modern",
    ":platform-neoforge-legacy",
    ":platform-neoforge-modern",
)

// runnable demos (not published, excluded from coverage aggregation)
include(
    ":examples:cli-sample",
    ":examples:jda-sample",
    ":examples:paper-sample",
    ":examples:fabric-sample",
    ":examples:neoforge-sample",
)

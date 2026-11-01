plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "me.catalysmrl"
version = "3.1.0"
description = "CataMines Fork - bounded regenerating mines for Paper 26.2 and 26.3"

val paperApiVersion = providers.gradleProperty("paperApiVersion").getOrElse("26.3.build.206-stable")
val mockBukkit = if (paperApiVersion.startsWith("26.2.")) "mockbukkit-v26.2:4.117.0" else "mockbukkit-v26.3:4.118.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // Compile against standard WorldEdit, not FAWE-only methods. Either provider
    // can implement these operations at runtime, but must support the server version.
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.6")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockbukkit.mockbukkit:$mockBukkit")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("com.sk89q.worldedit:worldedit-bukkit:7.4.6")
}

configurations.configureEach {
    if (name.endsWith("Classpath", ignoreCase = true)) {
        resolutionStrategy.force("io.papermc.paper:paper-api:$paperApiVersion")
    }
}

val smoke = sourceSets.create("smoke") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

tasks.register<Jar>("smokeJar") {
    description = "Builds the isolated-server regression plugin (never install on production)."
    archiveBaseName.set("catamines-smoke-tests")
    from(smoke.output)
}

tasks {
    processResources {
        filteringCharset = "UTF-8"

        filesMatching("plugin.yml") {
            expand(
                "pluginName" to "CataMines",
                "version" to project.version,
                "description" to (project.description ?: "CataMines Fork"),
                "main" to "me.catalysmrl.catamines.CataMines",
            )
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
        exclude("**/legacycatamines/**")
    }

    test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }

    shadowJar {
        dependsOn(processResources)

        archiveBaseName.set("catamines-fork")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        relocate("org.bstats", "me.catalysmrl.catamines.shaded.metrics")

        from(processResources) {
            include("plugin.yml")
        }

        from(processResources) {
            exclude("plugin.yml")
        }
    }

    jar {
        enabled = false // disable normal jar
    }

    build {
        dependsOn(shadowJar)
    }
}

plugins {
    id("com.gradleup.shadow") version "9.0.0-beta4"
    java
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

group = "normalmanv2.normalDiscGolf"
version = property("version") as String

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    maven("https://oss.sonatype.org/content/groups/public")
    maven("https://maven.enginehub.org/repo")
    maven("https://maven.radsteve.net/public")
    maven("https://maven.mcbrawls.net/releases")
    mavenCentral()
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.19")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.19")
    compileOnly("io.netty:netty-all:4.2.5.Final")

    implementation("org.NormalMan:NormalAPI:1.1")

    implementation("net.radstevee.packed:packed-core:1.1.2")

    implementation("net.mcbrawls.inject:api:3.1.2")
    implementation("net.mcbrawls.inject:spigot:3.1.2")
    implementation("net.mcbrawls.inject:http:3.1.2")
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    assemble {
        dependsOn(shadowJar)
        dependsOn(processResources)
    }

    shadowJar {
        fun relocate(packageName: String) = relocate(packageName, "normalmanv2.normalDiscGolf.shaded.$packageName")
        relocate("org.normal")
        relocate("net.mcbrawls.inject")
        relocate("net.radstevee.packed")

        dependencies {
            exclude(dependency("it.unimi.dsi:fastutil"))
            exclude(dependency("com.mojang:datafixerupper"))
        }
    }

    register("copyJars", Copy::class) {
        dependsOn(shadowJar)
        from(shadowJar)
        into("C:\\Users\\ronan\\Desktop\\localhost\\plugins")
    }

}
val regressionChecks by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("normalmanv2.normalDiscGolf.RegressionChecks")
}
tasks.check { dependsOn(regressionChecks) }

val smokeTest = sourceSets.create("smokeTest")
configurations[smokeTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
smokeTest.compileClasspath += sourceSets.main.get().output
smokeTest.runtimeClasspath += sourceSets.main.get().output
tasks.register<Jar>("smokeTestJar") {
    dependsOn(tasks.named(smokeTest.classesTaskName))
    archiveBaseName.set("NDGSmokeChecks")
    from(smokeTest.output)
}

import java.io.FileInputStream
import java.util.Properties

plugins {
    id("maven-publish")
}

val githubProperties = Properties()
githubProperties.load(FileInputStream(rootProject.file("github.properties")))

publishing {
    publications {
        register<MavenPublication>("release"){
            groupId = "com.paymenttools"
            artifactId = "paymenttoolssdk"
            version = "1.0.13"
            artifact("source/paymenttools-sdk-release.aar")
        }
    }

    repositories {
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/paymenttools/wlp-android-distribution")
            credentials{
                username = githubProperties.getProperty("gpr.usr")
                password = githubProperties.getProperty("gpr.key")
            }
        }
    }
}
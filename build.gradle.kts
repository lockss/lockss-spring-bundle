/*
 * LOCKSS Spring Bundle
 *
 * POM bundle and classes for LOCKSS projects using Spring Boot.
 */

plugins {
    id("lockss-java-conventions")
}

// This is a library bundle, not a Spring Boot application
// We still use Spring Boot dependency management but don't create a bootJar
apply(plugin = "io.spring.dependency-management")

version = "2.16.0-SNAPSHOT"
description = "POM bundle and classes for LOCKSS projects using Spring Boot"

// Export test JAR for other projects
val publishTestJar: Boolean by extra(true)

// Globally exclude Spring Boot's default logging to use Log4J2 instead
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
}

dependencies {
    // Internal dependencies
    api(project(":lockss-core"))
    api(project(":lockss-plugin-compat"))

    // Spring Boot starters
    api(libs.spring.boot.starter.web)
    api(libs.spring.boot.starter.tomcat)
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.validation)
    api(libs.spring.boot.starter.activemq) {
        exclude(group = "org.apache.activemq", module = "activemq-client-jakarta")
    }
    api(libs.spring.boot.starter.test) {
        exclude(group = "com.vaadin.external.google", module = "android-json")
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.junit.jupiter", module = "junit-jupiter-engine")
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }

    // JUnit Platform
    api(libs.junit.platform.commons)

    // Springdoc OpenAPI
    api(libs.springdoc.openapi.starter.webmvc.ui)

    // Jackson
    api(libs.jackson.databind)
    api(libs.jackson.dataformat.yaml)

    // Test dependencies
    testImplementation(platform(project(":lockss-pom-bundles:lockss-junit5-bundle")))
    testImplementation(libs.junit.jupiter.engine)
}

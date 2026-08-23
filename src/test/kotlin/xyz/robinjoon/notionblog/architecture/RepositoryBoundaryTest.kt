package xyz.robinjoon.notionblog.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RepositoryBoundaryTest {
    @Test
    fun `deployment harness manifests are not owned by this repository`() {
        assertThat(Path.of("deploy/helm")).doesNotExist()
    }

    @Test
    fun `docker image uses java 25 build and runtime stages with gradle wrapper`() {
        val dockerfile = Files.readString(Path.of("Dockerfile"))

        assertThat(dockerfile).contains("FROM eclipse-temurin:25-jdk")
        assertThat(dockerfile).contains("FROM eclipse-temurin:25-jre")
        assertThat(dockerfile).contains("./gradlew")
        assertThat(dockerfile).contains("bootJar")
        assertThat(dockerfile).contains("USER 10001:10001")
    }
}

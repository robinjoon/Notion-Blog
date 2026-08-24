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

    @Test
    fun `ci publishes immutable images without owning the deployment harness`() {
        val workflowPath = Path.of(".github/workflows/ci.yml")

        assertThat(workflowPath).isRegularFile()

        val workflow = Files.readString(workflowPath)

        assertThat(workflow).contains("./gradlew build")
        assertThat(workflow).contains("linux/amd64")
        assertThat(workflow)
            .contains("IMAGE_TAG: sha-\${{ github.sha }}-run-\${{ github.run_id }}-\${{ github.run_attempt }}")
        assertThat(workflow).contains("push: true")
        assertThat(workflow).contains("bootstrap-homelab")
        assertThat(workflow).contains("github.ref == 'refs/heads/master'")
        assertThat(workflow).contains("HOMELAB_REGISTRY_USERNAME")
        assertThat(workflow).contains("HOMELAB_REGISTRY_PASSWORD")
        assertThat(workflow)
            .doesNotContain("HARNESS_DEPLOY_KEY", "tools/platform.py", "git push origin HEAD:main")
        assertThat(workflow).doesNotContain("deploy-*", "kubectl", "KUBECONFIG")
    }
}

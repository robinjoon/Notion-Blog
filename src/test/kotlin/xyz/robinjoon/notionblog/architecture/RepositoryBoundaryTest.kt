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
        assertThat(workflow).contains("github.ref == 'refs/heads/master'")
        assertThat(workflow).contains("HOMELAB_REGISTRY_USERNAME")
        assertThat(workflow).contains("HOMELAB_REGISTRY_PASSWORD")
        assertThat(workflow)
            .doesNotContain("HARNESS_DEPLOY_KEY", "tools/platform.py", "git push origin HEAD:main")
        assertThat(workflow).doesNotContain("bootstrap-homelab", "deploy-*", "kubectl", "KUBECONFIG")
    }

    @Test
    fun `ci requests the harness release with the pushed tag after a default branch publish`() {
        val workflow = Files.readString(Path.of(".github/workflows/ci.yml"))
        val imagePushStep = workflow.indexOf("- name: Build and push immutable image")
        val releaseRequestStep = workflow.indexOf("- name: Request workload release")
        val freshnessGuard = workflow.indexOf("git ls-remote --exit-code origin refs/heads/master")
        val harnessCommand = workflow.indexOf("gh workflow run release-workload-image.yml")

        assertThat(imagePushStep).isGreaterThanOrEqualTo(0)
        assertThat(releaseRequestStep).isGreaterThan(imagePushStep)
        assertThat(freshnessGuard).isGreaterThan(releaseRequestStep)
        assertThat(harnessCommand).isGreaterThan(freshnessGuard)
        assertThat(workflow).contains(
            "group: notion-blog-ci-\${{ github.ref == 'refs/heads/master' && 'master' || github.run_id }}",
            "cancel-in-progress: \${{ github.ref == 'refs/heads/master' }}",
            "if: success() && github.ref == 'refs/heads/master'",
            "GH_TOKEN: \${{ secrets.HARNESS_ACTIONS_TOKEN }}",
            "tags: \${{ env.REGISTRY_HOST }}/\${{ env.REGISTRY_IMAGE }}:\${{ env.IMAGE_TAG }}",
            "if [[ \"\$latest_sha\" != \"\$GITHUB_SHA\" ]]",
            "--repo robinjoon/Simple-K3S-Herness",
            "--ref main",
            "-f app=notion-blog",
            "-f container=app",
            "-f tag=\"\$IMAGE_TAG\"",
        )
        assertThat(workflow).doesNotContain(":latest", "if: always()", "continue-on-error: true")
        assertThat(workflow.split("docker/build-push-action@")).hasSize(2)
    }

    @Test
    fun `readme documents the narrowly scoped harness actions token`() {
        val readme = Files.readString(Path.of("README.md"))

        assertThat(readme).contains(
            "HARNESS_ACTIONS_TOKEN",
            "robinjoon/Simple-K3S-Herness",
            "`Actions: write`",
        )
    }
}

package xyz.robinjoon.notionblog.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RefreshPolicyTest {
    private val now = Instant.parse("2026-06-29T00:00:00Z")

    @Test
    fun `schedules successful refresh at the shared interval`() {
        assertThat(RefreshPolicy.nextSuccessfulRefreshAt(now, Duration.ofMinutes(1)))
            .isEqualTo(Instant.parse("2026-06-29T00:01:00Z"))
    }

    @Test
    fun `schedules a failed page after two minutes plus positive jitter`() {
        assertThat(RefreshPolicy.nextPageFailureAt(now, 1))
            .isEqualTo(Instant.parse("2026-06-29T00:02:01Z"))
        assertThat(RefreshPolicy.nextPageFailureAt(now, 30))
            .isEqualTo(Instant.parse("2026-06-29T00:02:30Z"))
    }

    @Test
    fun `rejects page failure jitter outside one to thirty seconds`() {
        assertThatThrownBy { RefreshPolicy.nextPageFailureAt(now, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RefreshPolicy.nextPageFailureAt(now, 31) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `caps only the settings additional retry delay at one hour`() {
        assertThat(RefreshPolicy.nextSettingsFailureAt(now, Duration.ofMinutes(1), 20))
            .isEqualTo(Instant.parse("2026-06-29T01:01:00Z"))
    }
}

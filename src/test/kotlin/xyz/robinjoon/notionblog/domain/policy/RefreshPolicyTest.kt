package xyz.robinjoon.notionblog.domain.policy

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RefreshPolicyTest {
    private val now = Instant.parse("2026-06-29T00:00:00Z")

    @Test
    fun `schedules successful settings and pages at their base intervals`() {
        assertThat(RefreshPolicy.nextRefreshAt(RefreshTargetKind.SETTINGS, 0, now))
            .isEqualTo(Instant.parse("2026-06-29T00:01:00Z"))
        assertThat(RefreshPolicy.nextRefreshAt(RefreshTargetKind.PAGE, 0, now))
            .isEqualTo(Instant.parse("2026-06-29T00:15:00Z"))
    }

    @Test
    fun `adds exponential retry delay after failures`() {
        assertThat(RefreshPolicy.nextRefreshAt(RefreshTargetKind.PAGE, 1, now))
            .isEqualTo(Instant.parse("2026-06-29T00:20:00Z"))
        assertThat(RefreshPolicy.nextRefreshAt(RefreshTargetKind.PAGE, 2, now))
            .isEqualTo(Instant.parse("2026-06-29T00:25:00Z"))
    }

    @Test
    fun `caps only the additional retry delay at one hour`() {
        assertThat(RefreshPolicy.nextRefreshAt(RefreshTargetKind.SETTINGS, 20, now))
            .isEqualTo(Instant.parse("2026-06-29T01:01:00Z"))
    }
}

package xyz.robinjoon.notionblog.domain.policy

import java.time.Duration
import java.time.Instant

enum class RefreshTargetKind {
    SETTINGS,
    PAGE,
}

object RefreshPolicy {
    private val settingsInterval = Duration.ofMinutes(1)
    private val pageInterval = Duration.ofMinutes(15)
    private val initialFailureDelay = Duration.ofMinutes(5)
    private val maximumFailureDelay = Duration.ofHours(1)

    fun nextRefreshAt(kind: RefreshTargetKind, failureCount: Int, now: Instant): Instant {
        require(failureCount >= 0) { "failure count cannot be negative" }
        val baseInterval = if (kind == RefreshTargetKind.SETTINGS) settingsInterval else pageInterval
        return now.plus(baseInterval).plus(failureDelay(failureCount))
    }

    private fun failureDelay(failureCount: Int): Duration {
        if (failureCount == 0) {
            return Duration.ZERO
        }

        var delay = initialFailureDelay
        repeat(failureCount - 1) {
            delay = delay.multipliedBy(2)
            if (delay >= maximumFailureDelay) {
                return maximumFailureDelay
            }
        }
        return delay.coerceAtMost(maximumFailureDelay)
    }
}

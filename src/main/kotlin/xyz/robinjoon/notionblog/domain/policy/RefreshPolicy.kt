package xyz.robinjoon.notionblog.domain.policy

import java.time.Duration
import java.time.Instant

object RefreshPolicy {
    private val pageFailureDelay = Duration.ofMinutes(2)
    private val initialFailureDelay = Duration.ofMinutes(5)
    private val maximumFailureDelay = Duration.ofHours(1)

    fun nextSuccessfulRefreshAt(now: Instant, interval: Duration): Instant {
        require(!interval.isZero && !interval.isNegative) { "refresh interval must be positive" }
        return now.plus(interval)
    }

    fun nextPageFailureAt(now: Instant, jitterSeconds: Long): Instant {
        require(jitterSeconds in MINIMUM_PAGE_JITTER_SECONDS..MAXIMUM_PAGE_JITTER_SECONDS) {
            "page failure jitter must be between 1 and 30 seconds"
        }
        return now.plus(pageFailureDelay).plusSeconds(jitterSeconds)
    }

    fun nextSettingsFailureAt(now: Instant, interval: Duration, failureCount: Int): Instant {
        require(!interval.isZero && !interval.isNegative) { "refresh interval must be positive" }
        require(failureCount >= 0) { "failure count cannot be negative" }
        return now.plus(interval).plus(failureDelay(failureCount))
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

    private const val MINIMUM_PAGE_JITTER_SECONDS = 1L
    private const val MAXIMUM_PAGE_JITTER_SECONDS = 30L
}

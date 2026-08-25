package xyz.robinjoon.notionblog.application.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.application.port.output.source.RetryableSourceException
import xyz.robinjoon.notionblog.application.port.output.source.SourceAccessException
import xyz.robinjoon.notionblog.application.port.output.source.SourceAuthenticationException
import xyz.robinjoon.notionblog.application.port.output.source.SourceConfigurationException
import xyz.robinjoon.notionblog.application.port.output.source.SourceMappingException
import xyz.robinjoon.notionblog.domain.sync.SyncFailureKind

class SourceFailureClassificationTest {
    @Test
    fun `classifies each source exception by its concrete type`() {
        assertThat(RetryableSourceException().toSyncFailureKind()).isEqualTo(SyncFailureKind.RETRYABLE_SOURCE)
        assertThat(SourceAuthenticationException().toSyncFailureKind()).isEqualTo(SyncFailureKind.AUTHENTICATION)
        assertThat(SourceAccessException().toSyncFailureKind()).isEqualTo(SyncFailureKind.ACCESS)
        assertThat(SourceConfigurationException().toSyncFailureKind()).isEqualTo(SyncFailureKind.CONFIGURATION)
        assertThat(SourceMappingException().toSyncFailureKind()).isEqualTo(SyncFailureKind.MAPPING)
    }
}

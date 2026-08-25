package xyz.robinjoon.notionblog.application.port.output.source

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SourcePortContractTest {
    @Test
    fun `source failures are classified without source specific DTOs`() {
        assertThat(RetryableSourceException()).isInstanceOf(SourceException::class.java)
        assertThat(SourceAuthenticationException()).isInstanceOf(SourceException::class.java)
        assertThat(SourceAccessException()).isInstanceOf(SourceException::class.java)
        assertThat(SourceConfigurationException()).isInstanceOf(SourceException::class.java)
        assertThat(SourceMappingException()).isInstanceOf(SourceException::class.java)
    }
}

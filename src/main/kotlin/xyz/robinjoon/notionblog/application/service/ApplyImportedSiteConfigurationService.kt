package xyz.robinjoon.notionblog.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.notionblog.application.model.AppliedSiteConfiguration
import xyz.robinjoon.notionblog.application.model.ImportedSiteConfiguration
import xyz.robinjoon.notionblog.application.port.output.persistence.PublicationRepository
import xyz.robinjoon.notionblog.application.port.output.persistence.SiteConfigurationRepository
import xyz.robinjoon.notionblog.application.port.output.persistence.SyncStateRepository
import xyz.robinjoon.notionblog.application.port.output.presentation.PresentationAssetCatalog
import xyz.robinjoon.notionblog.domain.publication.BlogPublication
import xyz.robinjoon.notionblog.domain.publication.PublicationId
import xyz.robinjoon.notionblog.domain.site.PresentationAssetRef
import xyz.robinjoon.notionblog.domain.site.PresentationProfile
import xyz.robinjoon.notionblog.domain.site.PresentationProfileKey
import xyz.robinjoon.notionblog.domain.site.PresentationProfileRef
import xyz.robinjoon.notionblog.domain.site.SiteConfiguration
import xyz.robinjoon.notionblog.domain.site.SiteMetadata
import xyz.robinjoon.notionblog.domain.sync.RefreshPolicy
import xyz.robinjoon.notionblog.domain.sync.SyncFailureKind
import xyz.robinjoon.notionblog.domain.sync.SyncState
import xyz.robinjoon.notionblog.domain.sync.SyncTarget
import java.time.Clock
import java.time.Instant
import java.util.IllformedLocaleException
import java.util.Locale

@Transactional
class ApplyImportedSiteConfigurationService(
    private val siteConfigurationRepository: SiteConfigurationRepository,
    private val publicationRepository: PublicationRepository,
    private val presentationAssetCatalog: PresentationAssetCatalog,
    private val syncStateRepository: SyncStateRepository,
    private val clock: Clock,
    private val refreshPolicy: RefreshPolicy,
    private val defaultPresentationProfileKey: PresentationProfileKey,
    private val publicationIdFactory: () -> PublicationId,
) {
    @Transactional
    fun apply(imported: ImportedSiteConfiguration): AppliedSiteConfiguration {
        val now = clock.instant()
        val profile = resolveProfile(imported)
        val favicon = resolveFavicon(imported)
        val metadata = SiteMetadata(
            siteName = imported.metadata.siteName,
            defaultDescription = imported.metadata.defaultDescription,
            languageTag = validatedLanguageTag(imported.metadata.languageTag),
            favicon = favicon,
        )
        val current = siteConfigurationRepository.findCurrent()
        val publicationId = current?.publicationId ?: publicationIdFactory()
        val configuration = SiteConfiguration(
            publicationId = publicationId,
            rootDocument = imported.rootDocument,
            headerDocument = imported.headerDocument,
            footerDocument = imported.footerDocument,
            metadata = metadata,
            presentationProfile = profile.reference(),
        )

        if (current == null) {
            publicationRepository.save(BlogPublication(publicationId, null, null))
        }
        siteConfigurationRepository.save(configuration, now)
        recordSuccess(now)

        return AppliedSiteConfiguration(
            configuration = configuration,
            rootChanged = current?.rootDocument != imported.rootDocument,
        )
    }

    @Transactional
    fun recordFailure(kind: SyncFailureKind) {
        val now = clock.instant()
        val target = SyncTarget.SiteConfiguration
        val current = syncStateRepository.find(target)
        val nextFailureCount = Math.addExact(current?.failureCount ?: 0, 1)
        val refreshAfter = refreshPolicy.nextFailureRefreshAt(now, nextFailureCount)
        val updated = current?.recordFailure(kind, refreshAfter)
            ?: SyncState(target, null, refreshAfter, nextFailureCount, kind)

        syncStateRepository.save(updated)
    }

    private fun resolveProfile(imported: ImportedSiteConfiguration): PresentationProfile {
        val key = imported.presentationProfileKey ?: defaultPresentationProfileKey
        val profile = requireNotNull(siteConfigurationRepository.findCurrentProfile(key)) {
            "presentation profile must be a registered current profile: ${key.value}"
        }
        require(profile.key == key) { "resolved presentation profile key must match the requested key" }
        profile.styleSheets.forEach(::requireRegisteredAsset)
        profile.scripts.forEach(::requireRegisteredAsset)
        return profile
    }

    private fun resolveFavicon(imported: ImportedSiteConfiguration): PresentationAssetRef? = imported.metadata.faviconAssetKey?.let { key ->
        val resolved = requireNotNull(presentationAssetCatalog.resolveCurrent(key)) {
            "favicon must resolve to a registered current presentation asset: $key"
        }
        require(resolved.reference.key == key) {
            "resolved favicon key must match the requested key"
        }
        require(resolved.descriptor.integrity == resolved.reference.integrity) {
            "favicon descriptor integrity must match its reference"
        }
        resolved.reference
    }

    private fun requireRegisteredAsset(reference: PresentationAssetRef) {
        val descriptor = requireNotNull(presentationAssetCatalog.resolve(reference)) {
            "presentation profile asset must be registered exactly: ${reference.key}@${reference.version}"
        }
        require(descriptor.integrity == reference.integrity) {
            "presentation asset descriptor integrity must match its reference"
        }
    }

    private fun validatedLanguageTag(languageTag: String): String = try {
        val normalized = Locale.Builder().setLanguageTag(languageTag).build().toLanguageTag()
        require(normalized.equals(languageTag, ignoreCase = true)) { "language tag must be a valid BCP 47 tag" }
        normalized
    } catch (exception: IllformedLocaleException) {
        throw IllegalArgumentException("language tag must be a valid BCP 47 tag", exception)
    }

    private fun PresentationProfile.reference(): PresentationProfileRef = PresentationProfileRef(id, version)

    private fun recordSuccess(now: Instant) {
        val target = SyncTarget.SiteConfiguration
        val refreshAfter = refreshPolicy.nextSuccessfulRefreshAt(now)
        val updated = syncStateRepository.find(target)?.recordSuccess(now, refreshAfter)
            ?: SyncState(target, now, refreshAfter, 0, null)

        syncStateRepository.save(updated)
    }
}

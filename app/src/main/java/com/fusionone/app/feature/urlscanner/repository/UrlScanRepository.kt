package com.fusionone.app.feature.urlscanner.repository

import com.fusionone.app.core.database.ScanHistoryDao
import com.fusionone.app.core.database.ScanHistoryEntity
import com.fusionone.app.core.network.SafeBrowsingApi
import com.fusionone.app.core.network.SafeBrowsingRequest
import com.fusionone.app.core.network.UrlhausApi
import com.fusionone.app.core.util.CryptoManager
import com.fusionone.app.core.util.RedirectChainResolver
import com.fusionone.app.core.util.SslCertChecker
import com.fusionone.app.core.util.TyposquatDetector
import com.fusionone.app.core.util.WhoisClient
import com.fusionone.app.feature.urlscanner.model.UrlScanResult
import com.fusionone.app.feature.urlscanner.model.Verdict
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlScanRepository @Inject constructor(
    private val safeBrowsingApi: SafeBrowsingApi,
    private val urlhausApi: UrlhausApi,
    private val redirectChainResolver: RedirectChainResolver,
    private val sslCertChecker: SslCertChecker,
    private val whoisClient: WhoisClient,
    private val typosquatDetector: TyposquatDetector,
    private val scanHistoryDao: ScanHistoryDao,
    private val cryptoManager: CryptoManager
) {

    fun observeHistory(): Flow<List<ScanHistoryEntity>> = scanHistoryDao.observeAll()
    fun observeFavorites(): Flow<List<ScanHistoryEntity>> = scanHistoryDao.observeByVerdict("DANGER")
    fun search(query: String): Flow<List<ScanHistoryEntity>> = scanHistoryDao.search(query)
    suspend fun deleteHistory() = scanHistoryDao.clearAll()
    fun decryptUrl(entity: ScanHistoryEntity): String = cryptoManager.decrypt(entity.urlEncrypted, entity.urlIvBase64)

    suspend fun scan(rawUrl: String): UrlScanResult = coroutineScope {
        val normalizedUrl = normalize(rawUrl)
        val uri = URI(normalizedUrl)
        val domain = uri.host ?: normalizedUrl

        val redirectChainDeferred: Deferred<List<String>> = async {
            runCatching { redirectChainResolver.resolve(normalizedUrl) }.getOrDefault(listOf(normalizedUrl))
        }
        val safeBrowsingDeferred = async {
            runCatching {
                safeBrowsingApi.findThreatMatches(
                    body = SafeBrowsingRequest(
                        threatInfo = SafeBrowsingRequest.ThreatInfo(
                            threatEntries = listOf(SafeBrowsingRequest.ThreatEntry(normalizedUrl))
                        )
                    )
                ).matches.orEmpty()
            }.getOrDefault(emptyList())
        }
        val urlhausDeferred = async {
            runCatching { urlhausApi.lookupUrl(normalizedUrl) }.getOrNull()
        }
        val sslDeferred = async { sslCertChecker.check(domain) }
        val whoisDeferred = async { whoisClient.lookup(domain) }
        val ipDeferred = async {
            runCatching { InetAddress.getByName(domain).hostAddress }.getOrNull()
        }

        val redirectChain = redirectChainDeferred.await()
        val finalUrl = redirectChain.lastOrNull() ?: normalizedUrl
        val safeBrowsingMatches = safeBrowsingDeferred.await()
        val urlhausResult = urlhausDeferred.await()
        val ssl = sslDeferred.await()
        val whois = whoisDeferred.await()
        val ipAddress = ipDeferred.await()
        val typosquat = typosquatDetector.analyze(domain)

        val explanations = mutableListOf<String>()
        var score = 100

        if (safeBrowsingMatches.isNotEmpty()) {
            score -= 60
            explanations += "Google Safe Browsing flagged this URL for: " +
                safeBrowsingMatches.joinToString { it.threatType.lowercase().replace("_", " ") }
        }
        if (urlhausResult?.query_status == "ok") {
            score -= 50
            explanations += "URLhaus (abuse.ch) has this URL on record as a malware distribution point" +
                (urlhausResult.threat?.let { " ($it)" } ?: "") +
                (urlhausResult.url_status?.let { ", status: $it" } ?: "")
        }
        if (!ssl.isValid) {
            score -= if (ssl.isSelfSigned) 25 else 15
            explanations += ssl.error?.let { "TLS/SSL check failed: $it" }
                ?: if (ssl.isSelfSigned) "Certificate is self-signed" else "Certificate is expired or invalid"
        }
        if (typosquat.isSuspicious) {
            score -= 40
            explanations += "Domain closely resembles \"${typosquat.resemblesBrand}\" " +
                "(edit distance ${typosquat.editDistance}) — classic typosquatting pattern"
        }
        if (redirectChain.size > 2) {
            score -= 10
            explanations += "URL passes through ${redirectChain.size - 1} redirect hop(s) before reaching its destination"
        }
        val ageDays = whois.domainAgeDays
        if (ageDays != null && ageDays < 30) {
            score -= 20
            explanations += "Domain was registered only $ageDays day(s) ago — newly-registered domains are disproportionately used for phishing"
        }
        if (normalizedUrl != finalUrl && domain != URI(finalUrl).host) {
            explanations += "Redirects to a different domain: ${URI(finalUrl).host}"
        }
        score = score.coerceIn(0, 100)

        if (explanations.isEmpty()) {
            explanations += "No known threats found across Google Safe Browsing or URLhaus. TLS certificate is valid."
        }

        val verdict = when {
            score < 40 -> Verdict.DANGER
            score < 75 -> Verdict.WARNING
            else -> Verdict.SAFE
        }

        val result = UrlScanResult(
            originalUrl = rawUrl,
            finalUrl = finalUrl,
            domain = domain,
            verdict = verdict,
            reputationScore = score,
            threatExplanations = explanations,
            redirectChain = redirectChain,
            sslValid = ssl.isValid,
            sslIssuer = ssl.issuer,
            sslDaysUntilExpiry = ssl.daysUntilExpiry,
            domainAgeDays = whois.domainAgeDays,
            whoisRegistrar = whois.registrar,
            ipAddress = ipAddress,
            isTyposquat = typosquat.isSuspicious,
            resemblesBrand = typosquat.resemblesBrand,
            scannedAtEpochMillis = System.currentTimeMillis()
        )

        persist(result)
        result
    }

    private suspend fun persist(result: UrlScanResult) {
        val (cipher, iv) = cryptoManager.encrypt(result.originalUrl)
        scanHistoryDao.insert(
            ScanHistoryEntity(
                urlEncrypted = cipher,
                urlIvBase64 = iv,
                displayDomain = result.domain,
                verdict = result.verdict.name,
                reputationScore = result.reputationScore,
                threatTypes = result.threatExplanations,
                sslValid = result.sslValid,
                domainAgeDays = result.domainAgeDays,
                ipAddress = result.ipAddress,
                ipCountry = null,
                redirectChain = result.redirectChain,
                scannedAtEpochMillis = result.scannedAtEpochMillis
            )
        )
    }

    private fun normalize(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}

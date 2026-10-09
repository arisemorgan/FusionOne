package com.fusionone.app.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.inject.Inject
import javax.inject.Singleton

data class SslResult(
    val isValid: Boolean,
    val issuer: String? = null,
    val subject: String? = null,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val daysUntilExpiry: Long? = null,
    val isSelfSigned: Boolean = false,
    val error: String? = null
)

@Singleton
class SslCertChecker @Inject constructor() {

    suspend fun check(host: String, timeoutMs: Int = 8000): SslResult = withContext(Dispatchers.IO) {
        runCatching {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket() as SSLSocket).use { socket ->
                socket.connect(InetSocketAddress(host, 443), timeoutMs)
                socket.soTimeout = timeoutMs
                socket.startHandshake()

                val chain = socket.session.peerCertificates
                val leaf = chain.firstOrNull() as? X509Certificate
                    ?: return@withContext SslResult(isValid = false, error = "No certificate presented")

                val notBefore = leaf.notBefore.toInstant()
                val notAfter = leaf.notAfter.toInstant()
                val now = Instant.now()
                val expired = now.isAfter(notAfter) || now.isBefore(notBefore)
                val selfSigned = leaf.issuerX500Principal == leaf.subjectX500Principal

                SslResult(
                    isValid = !expired && !selfSigned,
                    issuer = leaf.issuerX500Principal?.name,
                    subject = leaf.subjectX500Principal?.name,
                    validFrom = notBefore,
                    validTo = notAfter,
                    daysUntilExpiry = java.time.Duration.between(now, notAfter).toDays(),
                    isSelfSigned = selfSigned
                )
            }
        }.getOrElse { e ->
            SslResult(isValid = false, error = e.message ?: "TLS handshake failed")
        }
    }
}

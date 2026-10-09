package com.fusionone.app.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class WhoisResult(
    val registrar: String? = null,
    val creationDate: String? = null,
    val domainAgeDays: Int? = null,
    val nameServers: List<String> = emptyList(),
    val rawSummary: String? = null
)

@Singleton
class WhoisClient @Inject constructor() {

    suspend fun lookup(domain: String): WhoisResult = withContext(Dispatchers.IO) {
        val bare = domain.removePrefix("www.")
        val ianaResponse = query("whois.iana.org", bare)
        val referServer = ianaResponse.lineSequence()
            .firstOrNull { it.startsWith("refer:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

        val full = if (referServer != null) query(referServer, bare) else ianaResponse
        parse(full)
    }

    private fun query(host: String, domain: String, timeoutMs: Int = 6000): String {
        return runCatching {
            Socket(host, 43).use { socket ->
                socket.soTimeout = timeoutMs
                OutputStreamWriter(socket.getOutputStream()).use { writer ->
                    writer.write("$domain\r\n")
                    writer.flush()
                    BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                        reader.readText()
                    }
                }
            }
        }.getOrDefault("")
    }

    private fun parse(raw: String): WhoisResult {
        if (raw.isBlank()) return WhoisResult(rawSummary = "WHOIS lookup unavailable")

        val registrar = extractField(raw, "Registrar:", "Sponsoring Registrar:")
        val creationRaw = extractField(raw, "Creation Date:", "created:", "Registered on:")
        val nameServers = raw.lineSequence()
            .filter { it.contains("Name Server:", ignoreCase = true) || it.trim().startsWith("nserver:", ignoreCase = true) }
            .mapNotNull { it.substringAfter(":").trim().takeIf(String::isNotBlank) }
            .distinct()
            .toList()

        val ageDays = creationRaw?.let { parseAgeDays(it) }

        return WhoisResult(
            registrar = registrar,
            creationDate = creationRaw,
            domainAgeDays = ageDays,
            nameServers = nameServers,
            rawSummary = raw.lineSequence().filter { it.isNotBlank() && !it.startsWith("%") && !it.startsWith("#") }
                .take(15).joinToString("\n")
        )
    }

    private fun extractField(raw: String, vararg labels: String): String? {
        for (label in labels) {
            raw.lineSequence().firstOrNull { it.trim().startsWith(label, ignoreCase = true) }?.let {
                return it.substringAfter(":").trim()
            }
        }
        return null
    }

    private fun parseAgeDays(rawDate: String): Int? {
        val candidates = listOf(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )
        val cleaned = rawDate.trim().substringBefore(" ").let { if (it.length >= 10) it else rawDate.trim() }
        for (fmt in candidates) {
            val parsed = runCatching { Instant.parse(rawDate.trim()) }.getOrNull()
                ?: runCatching { java.time.LocalDate.parse(cleaned, fmt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() }.getOrNull()
            if (parsed != null) {
                return ChronoUnit.DAYS.between(parsed, Instant.now()).toInt()
            }
        }
        return null
    }
}

package com.fusionone.app.core.util

import android.content.Context
import android.net.Uri
import kotlin.math.log2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PayloadRisk { CLEAN, SUSPICIOUS, DANGEROUS }

data class PayloadFinding(val description: String, val risk: PayloadRisk)

data class PayloadAnalysis(
    val risk: PayloadRisk,
    val findings: List<PayloadFinding>,
    val fileSizeBytes: Long,
    val trailingBytesAfterImageEnd: Int,
    val trailingDataEntropy: Double?
)

@Singleton
class PayloadDetector @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    private val jpegEoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    private val pngIend = "IEND".toByteArray(Charsets.US_ASCII)

    private val embeddedSignatures = listOf(
        "MZ" to (byteArrayOf(0x4D, 0x5A) to "Windows executable (PE/MZ header)"),
        "ELF" to (byteArrayOf(0x7F, 0x45, 0x4C, 0x46) to "Linux executable (ELF header)"),
        "PK_ZIP" to (byteArrayOf(0x50, 0x4B, 0x03, 0x04) to "Embedded ZIP archive"),
        "SCRIPT_TAG" to ("<script".toByteArray(Charsets.US_ASCII) to "Embedded <script> tag"),
        "PHP_TAG" to ("<?php".toByteArray(Charsets.US_ASCII) to "Embedded PHP code"),
        "SHEBANG" to ("#!/bin/".toByteArray(Charsets.US_ASCII) to "Embedded shell script")
    )

    suspend fun analyze(imageUri: Uri): PayloadAnalysis = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: return@withContext PayloadAnalysis(PayloadRisk.CLEAN, emptyList(), 0, 0, null)

        val findings = mutableListOf<PayloadFinding>()

        val endOfImageIndex = findImageEnd(bytes)
        val trailingBytes = if (endOfImageIndex != null && endOfImageIndex < bytes.size - 1) {
            bytes.size - 1 - endOfImageIndex
        } else 0

        var entropy: Double? = null
        if (trailingBytes > 8) {
            val trailingSlice = bytes.copyOfRange(endOfImageIndex!! + 1, bytes.size)
            entropy = shannonEntropy(trailingSlice)
            val risk = when {
                trailingBytes > 1024 && entropy > 7.5 -> PayloadRisk.DANGEROUS
                trailingBytes > 64 -> PayloadRisk.SUSPICIOUS
                else -> PayloadRisk.SUSPICIOUS
            }
            findings.add(
                PayloadFinding(
                    "Found $trailingBytes bytes of data appended after the image's real end-of-file " +
                        "marker (entropy ${"%.2f".format(entropy)} bits/byte). Legitimate images end at " +
                        "the EOI/IEND marker — trailing data like this is the standard way payloads are " +
                        "smuggled inside otherwise-valid image files.",
                    risk
                )
            )

            // IMPORTANT: signature scanning is restricted to the trailing slice only — NOT
            // the whole file. Compressed JPEG/PNG pixel data is high-entropy and will contain
            // any given 2-4 byte sequence (like "MZ") purely by chance; scanning the full file
            // for these signatures produces constant false positives on completely ordinary
            // photos. A real appended payload only ever lives in the genuinely-appended region,
            // so that's the only place worth checking.
            for ((_, sigPair) in embeddedSignatures) {
                val (signature, label) = sigPair
                val index = indexOf(trailingSlice, signature, startFrom = 0)
                if (index >= 0) {
                    findings.add(
                        PayloadFinding(
                            "$label detected inside the appended data, at offset $index within it.",
                            PayloadRisk.DANGEROUS
                        )
                    )
                }
            }
        }

        runCatching {
            val exif = androidx.exifinterface.media.ExifInterface(
                context.contentResolver.openInputStream(imageUri)!!
            )
            val userComment = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT)
            if (userComment != null && userComment.length > 2000) {
                findings.add(
                    PayloadFinding(
                        "EXIF UserComment field is unusually large (${userComment.length} characters) — " +
                            "can indicate data smuggled inside metadata rather than genuine comments.",
                        PayloadRisk.SUSPICIOUS
                    )
                )
            }
        }

        val overallRisk = when {
            findings.any { it.risk == PayloadRisk.DANGEROUS } -> PayloadRisk.DANGEROUS
            findings.any { it.risk == PayloadRisk.SUSPICIOUS } -> PayloadRisk.SUSPICIOUS
            else -> PayloadRisk.CLEAN
        }

        PayloadAnalysis(
            risk = overallRisk,
            findings = findings,
            fileSizeBytes = bytes.size.toLong(),
            trailingBytesAfterImageEnd = trailingBytes,
            trailingDataEntropy = entropy
        )
    }

    private fun findImageEnd(bytes: ByteArray): Int? {
        if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            for (i in bytes.size - 2 downTo 2) {
                if (bytes[i] == jpegEoi[0] && bytes[i + 1] == jpegEoi[1]) return i + 1
            }
        }
        if (bytes.size > 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            val idx = indexOf(bytes, pngIend, 8)
            if (idx >= 0) return idx + pngIend.size + 4 - 1
        }
        return null
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, startFrom: Int): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in startFrom..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun shannonEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val freq = IntArray(256)
        for (b in data) freq[b.toInt() and 0xFF]++
        var entropy = 0.0
        for (count in freq) {
            if (count == 0) continue
            val p = count.toDouble() / data.size
            entropy -= p * log2(p)
        }
        return entropy
    }
}

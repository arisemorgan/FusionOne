package com.fusionone.app.core.util

import retrofit2.HttpException
import java.net.UnknownHostException

/**
 * Turns raw exceptions (HttpException("HTTP 403"), UnknownHostException, etc.) into
 * messages that actually tell the person what to do, instead of a bare status code.
 */
fun Throwable.toFriendlyMessage(sourceName: String): String = when (this) {
    is HttpException -> when (code()) {
        401, 403 -> "$sourceName rejected the request (missing or invalid API key). " +
            "Add a valid key in local.properties — see the README for where to get a free one."
        429 -> "$sourceName's free-tier rate limit was hit — wait a moment and try again."
        in 500..599 -> "$sourceName is temporarily unavailable (server error). Try again shortly."
        else -> "$sourceName returned an unexpected error (HTTP ${code()})."
    }
    is UnknownHostException -> "No internet connection — check your network and try again."
    else -> message ?: "Something went wrong talking to $sourceName."
}

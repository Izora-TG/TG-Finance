package com.tgfinance.core.sync

import java.net.URI

/** Validates the user-entered server address. Only HTTPS is accepted. */
object ServerUrl {

    sealed interface Result {
        data class Valid(val url: String) : Result
        data object Empty : Result
        data object NotHttps : Result
        data object Invalid : Result
    }

    fun normalize(input: String): Result {
        val raw = input.trim()
        if (raw.isEmpty()) return Result.Empty
        val withScheme = (if ("://" in raw) raw else "https://$raw").trimEnd('/')
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return Result.Invalid
        if (!uri.scheme.equals("https", ignoreCase = true)) return Result.NotHttps
        if (uri.host.isNullOrBlank() || uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) {
            return Result.Invalid
        }
        return Result.Valid("https://" + uri.rawAuthority + (uri.rawPath ?: "").trimEnd('/'))
    }
}

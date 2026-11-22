package com.app.assistant.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

object LocalHostChecker {
    const val INSECURE_HTTP_ERROR_MESSAGE =
        "Insecure HTTP is only allowed for local or private network servers. Please use https:// for public endpoints to protect your API key."

    fun isAllowedUrl(url: HttpUrl): Boolean {
        if (url.isHttps) return true
        return isLocalOrPrivateHost(url.host)
    }

    /**
     * Checks if a host is local or private without triggering blocking DNS lookups.
     * Supported formats:
     * - Loopback: localhost, 127.0.0.0/8, ::1
     * - Private IPv4 (RFC 1918): 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
     * - Tailscale / CGNAT IPv4 (RFC 6598): 100.64.0.0/10
     * - Private IPv6: unique local (fc00::/7) and link-local (fe80::/10)
     * - Local/private hostnames: ends with .local, .lan, .home, .internal, .ts.net,
     *   or is a single-label hostname with no dots.
     */
    fun isLocalOrPrivateHost(host: String): Boolean {
        val cleanHost = host.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .lowercase(Locale.ROOT)

        if (cleanHost.isBlank()) return false

        // 1. Direct loopback hostname
        if (cleanHost == "localhost") return true

        // 2. Parse IPv4 literal directly without DNS lookup
        val ipv4Octets = parseIpv4(cleanHost)
        if (ipv4Octets != null) {
            val (b0, b1) = ipv4Octets
            return when {
                // Loopback: 127.0.0.0/8
                b0 == 127 -> true
                // Private IPv4 (RFC 1918): 10.0.0.0/8
                b0 == 10 -> true
                // Private IPv4 (RFC 1918): 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
                b0 == 172 && b1 in 16..31 -> true
                // Private IPv4 (RFC 1918): 192.168.0.0/16
                b0 == 192 && b1 == 168 -> true
                // Tailscale / CGNAT IPv4 (RFC 6598): 100.64.0.0/10 (100.64.0.0 - 100.127.255.255)
                b0 == 100 && b1 in 64..127 -> true
                // Any other IPv4 address is public (e.g. 8.8.8.8, 172.32.0.1, 100.128.0.1)
                else -> false
            }
        }

        // 3. Parse IPv6 literal directly without DNS lookup
        if (cleanHost.contains(':')) {
            val ipv6Groups = parseIpv6(cleanHost) ?: return false
            // Loopback ::1
            val isLoopback = (0..6).all { ipv6Groups[it] == 0 } && ipv6Groups[7] == 1
            if (isLoopback) return true

            // Unique local: fc00::/7 (first byte is 0xFC or 0xFD)
            val firstByte = ipv6Groups[0] ushr 8
            if ((firstByte and 0xFE) == 0xFC) return true

            // Link-local: fe80::/10 (first 10 bits are 1111111010)
            if ((ipv6Groups[0] and 0xFFC0) == 0xFE80) return true

            // Any other IPv6 address is public
            return false
        }

        // 4. Local/private domain suffixes
        val localSuffixes = listOf(".local", ".lan", ".home", ".internal", ".ts.net")
        if (localSuffixes.any { cleanHost.endsWith(it) && cleanHost.length > it.length }) {
            return true
        }

        // 5. Single-label hostname with no dots (e.g. Tailscale MagicDNS short hostnames like "my-laptop")
        if (!cleanHost.contains('.')) {
            return true
        }

        // Any other hostname (e.g. api.openai.com, example.com) is public
        return false
    }

    private fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3) return null
            if (!part.all { it in '0'..'9' }) return null
            val num = part.toIntOrNull() ?: return null
            if (num !in 0..255) return null
            octets[i] = num
        }
        return octets
    }

    private fun parseIpv6(host: String): IntArray? {
        val parts = if (host.contains("::")) {
            val split = host.split("::")
            if (split.size > 2) return null // Multiple "::" not allowed
            val left = if (split[0].isEmpty()) emptyList() else split[0].split(':')
            val right = if (split.size > 1 && split[1].isNotEmpty()) split[1].split(':') else emptyList()
            if (left.size + right.size > 7) return null
            val zerosNeeded = 8 - (left.size + right.size)
            val full = mutableListOf<String>()
            full.addAll(left)
            repeat(zerosNeeded) { full.add("0") }
            full.addAll(right)
            full
        } else {
            host.split(':')
        }

        if (parts.size != 8) return null
        val groups = IntArray(8)
        for (i in 0 until 8) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 4) return null
            val value = part.toIntOrNull(16) ?: return null
            groups[i] = value
        }
        return groups
    }
}

class LocalOnlyCleartextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.isHttps) {
            return chain.proceed(request)
        }

        if (!LocalHostChecker.isLocalOrPrivateHost(url.host)) {
            throw IOException(LocalHostChecker.INSECURE_HTTP_ERROR_MESSAGE)
        }

        return chain.proceed(request)
    }
}

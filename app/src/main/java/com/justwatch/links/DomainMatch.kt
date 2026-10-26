package com.justwatch.links

internal object DomainMatch {
    fun matches(host: String, pattern: String): Boolean =
        host == pattern || (pattern.startsWith("*.") && host.endsWith(pattern.substring(1)))
}

package com.justwatch.links

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build

/** Read-only diagnostics. Domain approval can only be changed in Android settings. */
object LinkAssociations {
    data class Handler(val packageName: String, val label: String, val hosts: List<String>)
    data class Report(val allowed: Boolean, val states: Map<String, Int>, val handlers: List<Handler>, val candidates: List<String> = emptyList(), val error: String? = null) {
        fun enabled(host: String) = allowed && states[host] in setOf(
            DomainVerificationUserState.DOMAIN_STATE_SELECTED, DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
        fun summary() = groups.entries.joinToString("\n") { (name, hosts) ->
            "$name: ${hosts.count { enabled(it) }} of ${hosts.size} addresses enabled"
        }
        fun details() = buildString {
            appendLine("JustWatch ${contextVersion} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}; build ${Build.DISPLAY}")
            appendLine("Captured: ${java.time.Instant.now()}")
            appendLine("JustWatch link handling allowed: $allowed")
            error?.let { appendLine("Read error: $it") }
            for ((name, hosts) in groups) {
                appendLine("\n$name")
                for (host in hosts) appendLine("$host: ${if (enabled(host)) "enabled" else "not enabled"} (raw=${states[host] ?: "missing"})")
            }
            if (handlers.isNotEmpty()) appendLine("\nOther enabled handlers:")
            for (app in handlers) appendLine("${app.label} (${app.packageName}): ${app.hosts.joinToString()}")
            appendLine("\nVisible matching apps and their recorded states:")
            candidates.forEach { appendLine(it) }
            appendLine("\nStates: 0=unselected, 1=user-selected, 2=verified. Visibility is limited by Android; this is not a complete system ownership dump.")
        }
    }
    private val contextVersion get() = BuildConfig.VERSION_NAME
    val groups = linkedMapOf(
        "Instagram" to listOf("instagram.com", "www.instagram.com"),
        "Facebook" to listOf("facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch"),
        "TikTok" to listOf("tiktok.com", "www.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
    )

    fun read(context: Context): Report {
        if (Build.VERSION.SDK_INT < 31) return Report(false, emptyMap(), emptyList())
        val manager = context.getSystemService(DomainVerificationManager::class.java)
        val ownResult = runCatching { manager.getDomainVerificationUserState(context.packageName) }
        val own = ownResult.getOrNull()
        val packages = linkedMapOf<String, String>()
        val packageManager = context.packageManager
        val knownPackages = listOf("com.facebook.katana", "com.facebook.lite", "com.instagram.android",
            "com.instagram.lite", "com.facebook.appmanager", "com.facebook.system", "com.facebook.services",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill") +
            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                listOf("com.justwatch.links.fixture") else emptyList()
        for (name in knownPackages) {
            @Suppress("DEPRECATION")
            val info = runCatching { packageManager.getApplicationInfo(name, 0) }.getOrNull() ?: continue
            packages[name] = info.loadLabel(packageManager).toString()
        }
        val candidates = mutableListOf<String>()
        val handlers = packages.mapNotNull { (name, label) ->
            val result = runCatching { manager.getDomainVerificationUserState(name) }
            val state = result.getOrNull()
            candidates += "$label ($name): allowed=${state?.isLinkHandlingAllowed}; hosts=${state?.hostToStateMap?.toSortedMap()}; error=${result.exceptionOrNull()?.javaClass?.simpleName ?: "none"}"
            if (state == null || !state.isLinkHandlingAllowed) null else {
                val hosts = groups.values.flatten().filter { host ->
                    state.hostToStateMap.any { (pattern, value) ->
                        DomainMatch.matches(host, pattern) && value in setOf(DomainVerificationUserState.DOMAIN_STATE_SELECTED,
                            DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
                    }
                }
                if (hosts.isEmpty()) null else Handler(name, label, hosts)
            }
        }
        return Report(own?.isLinkHandlingAllowed ?: false, own?.hostToStateMap.orEmpty(), handlers, candidates, ownResult.exceptionOrNull()?.javaClass?.simpleName)
    }
}


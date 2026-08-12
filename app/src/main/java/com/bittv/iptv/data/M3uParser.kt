package com.bittv.iptv.data

import java.net.URI

object M3uParser {
    private val attributeRegex = Regex("([A-Za-z0-9_-]+)\\s*=\\s*\\\"([^\\\"]*)\\\"") 

    fun parse(text: String, baseUrl: String? = null, defaultHeaders: Map<String, String> = emptyMap()): List<Channel> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val result = ArrayList<Channel>()
        var pending: Pending? = null
        var generatedId = 0

        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                }
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolveUrl(line, baseUrl) ?: continue
                    val p = pending
                    val name = p?.name?.takeIf { it.isNotBlank() } ?: url
                    val id = p?.id?.takeIf { it.isNotBlank() } ?: "channel-${generatedId++}"
                    result += Channel(
                        id = id,
                        name = name,
                        logoUrl = p?.logoUrl,
                        group = p?.group ?: "Ungrouped",
                        streamUrl = url,
                        headers = defaultHeaders
                    )
                    pending = null
                }
            }
        }
        return result
    }

    private fun parseExtInf(line: String): Pending {
        val comma = line.indexOf(',')
        val attributePart = if (comma >= 0) line.substring(0, comma) else line
        val displayName = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attrs = attributeRegex.findAll(attributePart).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return Pending(
            id = attrs["tvg-id"],
            name = attrs["tvg-name"] ?: displayName,
            logoUrl = attrs["tvg-logo"],
            group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Ungrouped"
        )
    }

    private fun resolveUrl(value: String, baseUrl: String?): String? {
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        if (baseUrl.isNullOrBlank()) return null
        return runCatching { URI(baseUrl).resolve(value).toString() }.getOrNull()
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private data class Pending(
        val id: String?,
        val name: String?,
        val logoUrl: String?,
        val group: String
    )
}
package com.justwatch.links

import com.yausername.youtubedl_android.mapper.VideoInfo
import java.net.URI

/** Keep the selected stream's request headers together with its signed URL. */
data class ExtractedMedia(val url: String, val headers: Map<String, String> = emptyMap()) {
    companion object {
        fun fromInfo(info: VideoInfo): ExtractedMedia? {
            val url = info.url ?: return null
            val uri =
                try {
                    URI(url)
                } catch (_: Exception) {
                    return null
                }
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null)
                return null
            return ExtractedMedia(url, info.httpHeaders.orEmpty().toMap())
        }
    }
}

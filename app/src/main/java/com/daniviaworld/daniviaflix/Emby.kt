package com.daniviaworld.daniviaflix

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class Session(val server: String, val userId: String, val token: String, val userName: String)

data class Item(
    val id: String,
    val name: String,
    val type: String,
    val overview: String,
    val year: Int?,
    val rating: Double?,
    val runtimeMin: Int?,
    val collectionType: String?,
    val seriesId: String?,
    val seriesName: String?,
    val seasonNo: Int?,
    val episodeNo: Int?,
    val resumeMs: Long,
    val primaryTag: String?,
    val backdropTag: String?,
    val played: Boolean,
    val seriesPrimaryTag: String? = null,
)

data class SubInfo(val url: String, val mime: String, val lang: String?, val label: String)

data class PlayPlan(
    val url: String,
    val method: String,
    val playSessionId: String,
    val mediaSourceId: String,
    val startMs: Long,
    val subs: List<SubInfo>,
)

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).ifEmpty { null } else null

private fun JSONObject.intOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

fun JSONObject.toItem(): Item {
    val tags = optJSONObject("ImageTags")
    val ud = optJSONObject("UserData")
    val bd = optJSONArray("BackdropImageTags")
    return Item(
        id = optString("Id"),
        name = optString("Name"),
        type = optString("Type"),
        overview = optString("Overview"),
        year = intOrNull("ProductionYear"),
        rating = if (has("CommunityRating") && !isNull("CommunityRating")) optDouble("CommunityRating") else null,
        runtimeMin = if (has("RunTimeTicks") && !isNull("RunTimeTicks")) (optLong("RunTimeTicks") / 600_000_000L).toInt() else null,
        collectionType = str("CollectionType"),
        seriesId = str("SeriesId"),
        seriesName = str("SeriesName"),
        seasonNo = intOrNull("ParentIndexNumber"),
        episodeNo = intOrNull("IndexNumber"),
        resumeMs = (ud?.optLong("PlaybackPositionTicks") ?: 0L) / 10_000L,
        primaryTag = tags?.str("Primary"),
        backdropTag = if (bd != null && bd.length() > 0) bd.optString(0) else null,
        played = ud?.optBoolean("Played") ?: false,
        seriesPrimaryTag = str("SeriesPrimaryImageTag"),
    )
}

fun friendlyError(e: Throwable): String {
    val m = e.message ?: ""
    return when {
        m.contains("401") -> "Usuario o contraseña incorrectos."
        m.contains("403") -> "El servidor no permite este acceso."
        e is UnknownHostException || e is ConnectException || e is SocketTimeoutException ->
            "No se puede conectar con el servidor. Revisa la dirección y el puerto."
        m.isNotBlank() -> m
        else -> "Error inesperado."
    }
}

/** Qué sabe decodificar ESTE dispositivo. Con esto el servidor decide si envía el original o lo transcodifica. */
object Caps {
    private val infos: List<MediaCodecInfo> by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decoders(mime: String) = infos.filter { i -> i.supportedTypes.any { it.equals(mime, true) } }

    fun has(mime: String) = decoders(mime).isNotEmpty()

    /** Resolución máxima que decodifica el hardware (4K, 1080p o 720p). */
    fun maxSize(mime: String): Pair<Int, Int> {
        val sizes = listOf(7680 to 4320, 4096 to 2160, 3840 to 2160, 1920 to 1080, 1280 to 720)
        for ((w, h) in sizes) {
            val ok = decoders(mime).any { i ->
                try {
                    i.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(w, h) == true
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) return w to h
        }
        return 1280 to 720
    }

    fun supportsHevc10(): Boolean = decoders("video/hevc").any { i ->
        try {
            i.getCapabilitiesForType("video/hevc").profileLevels.any {
                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            }
        } catch (e: Exception) {
            false
        }
    }
}

class EmbyClient(ctx: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val deviceId: String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "danivia-device"

    var session: Session? = null

    private val fields = "Overview,ProductionYear,CommunityRating,RunTimeTicks,UserData,ImageTags,BackdropImageTags,SeriesId,SeriesName,SeriesPrimaryImageTag,ParentIndexNumber,IndexNumber"

    private fun authHeader(): String {
        val model = Build.MODEL.replace("\"", "")
        return "MediaBrowser Client=\"DaniviaFlix\", Device=\"$model\", DeviceId=\"$deviceId\", Version=\"0.1\""
    }

    private suspend fun call(method: String, url: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url)
                .header("X-Emby-Authorization", authHeader())
                .header("Accept", "application/json")
            session?.let { b.header("X-Emby-Token", it.token) }
            if (method == "POST") {
                b.post((body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
            } else {
                b.get()
            }
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                text
            }
        }

    private fun uid() = session?.userId ?: throw IOException("Sin sesión")

    private fun u(path: String, vararg q: Pair<String, String>): String {
        val s = session ?: throw IOException("Sin sesión")
        val qs = q.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
        return "${s.server}/emby$path" + if (qs.isNotEmpty()) "?$qs" else ""
    }

    private fun normalize(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        if (s.endsWith("/emby")) s = s.removeSuffix("/emby")
        return s
    }

    suspend fun login(serverRaw: String, user: String, pass: String): Session {
        val server = normalize(serverRaw)
        session = null
        val body = JSONObject().put("Username", user).put("Pw", pass)
        val j = JSONObject(call("POST", "$server/emby/Users/AuthenticateByName", body))
        val s = Session(server, j.getJSONObject("User").getString("Id"), j.getString("AccessToken"), user)
        session = s
        return s
    }

    private fun parseItems(txt: String): List<Item> {
        val arr = JSONObject(txt).optJSONArray("Items") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).toItem() }
    }

    /** Bibliotecas en el mismo orden que las tienes configuradas en Emby. */
    suspend fun views(): List<Item> = parseItems(call("GET", u("/Users/${uid()}/Views")))

    suspend fun resume(): List<Item> = parseItems(
        call("GET", u("/Users/${uid()}/Items/Resume", "Limit" to "16", "MediaTypes" to "Video", "Fields" to fields))
    )

    suspend fun latest(): List<Item> = parseItems(
        call(
            "GET",
            u(
                "/Users/${uid()}/Items",
                "SortBy" to "DateCreated", "SortOrder" to "Descending", "Recursive" to "true",
                "IncludeItemTypes" to "Movie,Series", "Limit" to "24", "Fields" to fields,
            )
        )
    )

    suspend fun library(view: Item, sortBy: String, order: String): List<Item> {
        val types = when (view.collectionType) {
            "movies" -> "Movie"
            "tvshows" -> "Series"
            "boxsets" -> "BoxSet"
            else -> null
        }
        val q = mutableListOf("ParentId" to view.id, "SortBy" to sortBy, "SortOrder" to order, "Fields" to fields)
        if (types != null) {
            q += "Recursive" to "true"
            q += "IncludeItemTypes" to types
        }
        return parseItems(call("GET", u("/Users/${uid()}/Items", *q.toTypedArray())))
    }

    suspend fun children(parentId: String): List<Item> = parseItems(
        call("GET", u("/Users/${uid()}/Items", "ParentId" to parentId, "SortBy" to "ProductionYear,SortName", "Fields" to fields))
    )

    suspend fun item(id: String): Item =
        JSONObject(call("GET", u("/Users/${uid()}/Items/$id", "Fields" to fields))).toItem()

    suspend fun seasons(seriesId: String): List<Item> = parseItems(
        call("GET", u("/Shows/$seriesId/Seasons", "UserId" to uid(), "Fields" to fields))
    )

    suspend fun episodes(seriesId: String, seasonId: String): List<Item> = parseItems(
        call("GET", u("/Shows/$seriesId/Episodes", "UserId" to uid(), "SeasonId" to seasonId, "Fields" to fields))
    )

    suspend fun random(): Item? = parseItems(
        call(
            "GET",
            u(
                "/Users/${uid()}/Items",
                "SortBy" to "Random", "Recursive" to "true", "IncludeItemTypes" to "Movie", "Limit" to "1", "Fields" to fields,
            )
        )
    ).firstOrNull()

    fun image(item: Item, type: String = "Primary", maxHeight: Int = 500): String? {
        val s = session ?: return null
        val tag = (if (type == "Backdrop") item.backdropTag else item.primaryTag) ?: return null
        return "${s.server}/emby/Items/${item.id}/Images/$type?maxHeight=$maxHeight&quality=90&tag=$tag&api_key=${s.token}"
    }

    /** Póster vertical: para un episodio usa el póster de su serie. */
    fun poster(item: Item, maxHeight: Int = 600): String? {
        val s = session ?: return null
        val sid = item.seriesId
        val stag = item.seriesPrimaryTag
        if (item.type == "Episode" && sid != null && stag != null) {
            return "${s.server}/emby/Items/$sid/Images/Primary?maxHeight=$maxHeight&quality=90&tag=$stag&api_key=${s.token}"
        }
        return image(item, "Primary", maxHeight)
    }

    /** Lo último añadido dentro de una biblioteca (como las filas de la portada de Emby). */
    suspend fun latestIn(view: Item): List<Item> {
        val txt = call(
            "GET",
            u("/Users/${uid()}/Items/Latest", "ParentId" to view.id, "Limit" to "20", "GroupItems" to "true", "Fields" to fields)
        )
        val arr = JSONArray(txt)
        return (0 until arr.length()).map { arr.getJSONObject(it).toItem() }
    }

    // ---------- Categorías y "momentos" ----------

    private fun typesFor(view: Item?) = when (view?.collectionType) {
        "movies" -> "Movie"
        "tvshows" -> "Series"
        "boxsets" -> "BoxSet"
        else -> null
    }

    /** Categorías (géneros) que existen dentro de una biblioteca, tal como las tienes en Emby. */
    suspend fun genres(view: Item): List<String> {
        val q = mutableListOf("ParentId" to view.id, "UserId" to uid(), "SortBy" to "SortName", "Recursive" to "true")
        typesFor(view)?.let { q += "IncludeItemTypes" to it }
        val arr = JSONObject(call("GET", u("/Genres", *q.toTypedArray()))).optJSONArray("Items") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("Name") }.filter { it.isNotBlank() }
    }

    suspend fun byGenre(view: Item, genre: String, limit: Int, sortBy: String = "SortName", order: String = "Ascending"): List<Item> {
        val q = mutableListOf(
            "ParentId" to view.id, "Genres" to genre, "Recursive" to "true", "SortBy" to sortBy,
            "SortOrder" to order, "Limit" to limit.toString(), "Fields" to fields,
        )
        typesFor(view)?.let { q += "IncludeItemTypes" to it }
        return parseItems(call("GET", u("/Users/${uid()}/Items", *q.toTypedArray())))
    }

    /** Cuántos títulos hay en una biblioteca. */
    suspend fun countIn(view: Item): Int {
        val q = mutableListOf("ParentId" to view.id, "Recursive" to "true", "Limit" to "1")
        typesFor(view)?.let { q += "IncludeItemTypes" to it }
        return JSONObject(call("GET", u("/Users/${uid()}/Items", *q.toTypedArray()))).optInt("TotalRecordCount")
    }

    /** Tres carátulas al azar de una biblioteca, para su tarjeta en abanico. */
    suspend fun sample(view: Item): List<Item> {
        val q = mutableListOf(
            "ParentId" to view.id, "Recursive" to "true", "SortBy" to "Random", "Limit" to "10", "Fields" to fields,
        )
        typesFor(view)?.let { q += "IncludeItemTypes" to it }
            ?: run { q += "IncludeItemTypes" to "Movie,Series,BoxSet" }
        return parseItems(call("GET", u("/Users/${uid()}/Items", *q.toTypedArray()))).filter { it.primaryTag != null }.take(3)
    }

    /** "¿Qué te apetece?": short = pelis cortas, epic = largas y bien valoradas, kids = familia, marathon = series. */
    suspend fun mood(kind: String): List<Item> {
        val base = mutableListOf("Recursive" to "true", "Fields" to fields)
        return when (kind) {
            "short" -> parseItems(call("GET", u("/Users/${uid()}/Items", *(base + listOf("IncludeItemTypes" to "Movie", "SortBy" to "Random", "Limit" to "300")).toTypedArray())))
                .filter { (it.runtimeMin ?: 0) in 1..95 }.take(60)
            "epic" -> parseItems(call("GET", u("/Users/${uid()}/Items", *(base + listOf("IncludeItemTypes" to "Movie", "SortBy" to "CommunityRating", "SortOrder" to "Descending", "Limit" to "300")).toTypedArray())))
                .filter { (it.runtimeMin ?: 0) >= 120 }.take(60)
            "kids" -> parseItems(call("GET", u("/Users/${uid()}/Items", *(base + listOf("IncludeItemTypes" to "Movie,Series", "Genres" to "Animación|Animation|Familia|Family|Infantil|Children|Kids|Familiar", "SortBy" to "Random", "Limit" to "80")).toTypedArray())))
            else -> parseItems(call("GET", u("/Users/${uid()}/Items", *(base + listOf("IncludeItemTypes" to "Series", "SortBy" to "Random", "Limit" to "60")).toTypedArray())))
        }
    }

    // ---------- Reproducción ----------

    private fun cond(prop: String, value: Int) = JSONObject()
        .put("Condition", "LessThanEqual").put("Property", prop)
        .put("Value", value.toString()).put("IsRequired", true)

    private fun deviceProfile(forceTranscode: Boolean): JSONObject {
        val video = mutableListOf("h264")
        val codecProfiles = JSONArray()
        val candidates = listOf(
            "h264" to "video/avc",
            "hevc" to "video/hevc",
            "vp9" to "video/x-vnd.on2.vp9",
            "av1" to "video/av01",
            "mpeg4" to "video/mp4v-es",
            "mpeg2video" to "video/mpeg2",
        )
        for ((name, mime) in candidates) {
            if (name != "h264" && !Caps.has(mime)) continue
            if (name != "h264") video += name
            val (w, h) = Caps.maxSize(mime)
            val conds = JSONArray().put(cond("Width", w)).put(cond("Height", h))
            if (name == "h264" || (name == "hevc" && !Caps.supportsHevc10())) conds.put(cond("VideoBitDepth", 8))
            codecProfiles.put(JSONObject().put("Type", "Video").put("Codec", name).put("Conditions", conds))
        }

        val audio = mutableListOf("aac", "mp3")
        listOf(
            "ac3" to "audio/ac3", "eac3" to "audio/eac3", "opus" to "audio/opus",
            "vorbis" to "audio/vorbis", "flac" to "audio/flac", "dts" to "audio/vnd.dts", "truehd" to "audio/true-hd",
        ).forEach { (n, m) -> if (Caps.has(m)) audio += n }

        val direct = JSONArray()
        if (!forceTranscode) {
            direct.put(
                JSONObject().put("Type", "Video")
                    .put("Container", "mkv,mp4,m4v,mov,webm,ts,m2ts,mpegts")
                    .put("VideoCodec", video.joinToString(","))
                    .put("AudioCodec", audio.joinToString(","))
            )
        }

        val transcode = JSONArray().put(
            JSONObject().put("Type", "Video").put("Container", "ts").put("Protocol", "hls")
                .put("Context", "Streaming").put("VideoCodec", "h264").put("AudioCodec", "aac")
                .put("MaxAudioChannels", "6")
        )

        val subs = JSONArray()
        listOf("srt", "subrip", "ass", "ssa", "pgssub", "dvdsub", "vtt").forEach {
            subs.put(JSONObject().put("Format", it).put("Method", "Embed"))
        }
        listOf("srt", "subrip", "vtt").forEach {
            subs.put(JSONObject().put("Format", it).put("Method", "External"))
        }

        return JSONObject()
            .put("Name", "DaniviaFlix")
            .put("MaxStreamingBitrate", 200_000_000)
            .put("MaxStaticBitrate", 200_000_000)
            .put("MusicStreamingTranscodingBitrate", 192_000)
            .put("DirectPlayProfiles", direct)
            .put("TranscodingProfiles", transcode)
            .put("CodecProfiles", codecProfiles)
            .put("SubtitleProfiles", subs)
    }

    private fun subMime(codec: String) = when (codec.lowercase()) {
        "vtt", "webvtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }

    suspend fun planPlayback(item: Item, startMs: Long, forceTranscode: Boolean): PlayPlan {
        val s = session ?: throw IOException("Sin sesión")
        val q = u(
            "/Items/${item.id}/PlaybackInfo",
            "UserId" to s.userId,
            "StartTimeTicks" to (startMs * 10_000).toString(),
            "MaxStreamingBitrate" to "200000000",
            "IsPlayback" to "true",
            "AutoOpenLiveStream" to "true",
        )
        val j = JSONObject(call("POST", q, JSONObject().put("DeviceProfile", deviceProfile(forceTranscode))))
        val sources = j.optJSONArray("MediaSources")
        if (sources == null || sources.length() == 0) throw IOException("El servidor no devolvió ningún archivo para este vídeo.")
        val ms = sources.getJSONObject(0)
        val msId = ms.getString("Id")
        val psid = j.optString("PlaySessionId")

        fun abs(p: String): String {
            val full = when {
                p.startsWith("http") -> p
                p.startsWith("/emby") -> s.server + p
                p.startsWith("/") -> s.server + "/emby" + p
                else -> s.server + "/emby/" + p
            }
            return if (full.contains("api_key=")) full else full + (if (full.contains("?")) "&" else "?") + "api_key=" + s.token
        }

        val method: String
        val url: String
        when {
            ms.optBoolean("SupportsDirectPlay") && !forceTranscode -> {
                method = "DirectPlay"
                val c = ms.optString("Container", "mkv").substringBefore(',').ifEmpty { "mkv" }
                url = "${s.server}/emby/Videos/${item.id}/stream.$c?Static=true&MediaSourceId=$msId" +
                    "&PlaySessionId=$psid&DeviceId=$deviceId&api_key=${s.token}"
            }
            ms.optString("DirectStreamUrl").isNotEmpty() && ms.optBoolean("SupportsDirectStream") && !forceTranscode -> {
                method = "DirectStream"
                url = abs(ms.getString("DirectStreamUrl"))
            }
            ms.optString("TranscodingUrl").isNotEmpty() -> {
                method = "Transcode"
                url = abs(ms.getString("TranscodingUrl"))
            }
            else -> throw IOException("El servidor no puede reproducir este vídeo en este dispositivo.")
        }

        val subs = mutableListOf<SubInfo>()
        val streams = ms.optJSONArray("MediaStreams")
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val st = streams.getJSONObject(i)
                if (st.optString("Type") == "Subtitle" && st.optString("DeliveryMethod") == "External" &&
                    st.optString("DeliveryUrl").isNotEmpty()
                ) {
                    subs += SubInfo(
                        url = abs(st.getString("DeliveryUrl")),
                        mime = subMime(st.optString("Codec", "srt")),
                        lang = st.str("Language"),
                        label = st.optString("DisplayTitle").ifEmpty { "Subtítulos" },
                    )
                }
            }
        }
        return PlayPlan(url, method, psid, msId, startMs, subs)
    }

    /** kind: "start" | "progress" | "stop" — así Emby recuerda por dónde vas ("Continuar viendo"). */
    suspend fun report(kind: String, plan: PlayPlan, itemId: String, posMs: Long, paused: Boolean) {
        val path = when (kind) {
            "start" -> "/Sessions/Playing"
            "stop" -> "/Sessions/Playing/Stopped"
            else -> "/Sessions/Playing/Progress"
        }
        val b = JSONObject()
            .put("ItemId", itemId)
            .put("MediaSourceId", plan.mediaSourceId)
            .put("PlaySessionId", plan.playSessionId)
            .put("PositionTicks", posMs * 10_000)
            .put("IsPaused", paused)
            .put("PlayMethod", plan.method)
        try {
            call("POST", u(path), b)
        } catch (e: Exception) {
            // Informar del progreso es secundario: nunca debe romper la reproducción.
        }
    }
}

package com.daeri.gpsspoofer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PlaceResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

class KakaoSearchClient(private val restApiKey: String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun searchKeyword(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (restApiKey.isBlank() || restApiKey == "PUT_KAKAO_REST_API_KEY_HERE") return@withContext emptyList()

        val url = "https://dapi.kakao.com/v2/local/search/keyword.json" +
                "?query=" + java.net.URLEncoder.encode(query, "UTF-8") +
                "&size=15"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "KakaoAK $restApiKey")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            parsePlaces(body)
        }
    }

    suspend fun searchAddress(query: String): PlaceResult? = withContext(Dispatchers.IO) {
        if (restApiKey.isBlank() || restApiKey == "PUT_KAKAO_REST_API_KEY_HERE") return@withContext null

        val url = "https://dapi.kakao.com/v2/local/search/address.json" +
                "?query=" + java.net.URLEncoder.encode(query, "UTF-8") +
                "&size=1"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "KakaoAK $restApiKey")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            parsePlaces(body).firstOrNull()
        }
    }

    private fun parsePlaces(body: String): List<PlaceResult> {
        return runCatching {
            val root = JSONObject(body)
            val docs = root.getJSONArray("documents")
            (0 until docs.length()).mapNotNull { i ->
                val o = docs.getJSONObject(i)
                val lat = o.optString("y").toDoubleOrNull() ?: return@mapNotNull null
                val lng = o.optString("x").toDoubleOrNull() ?: return@mapNotNull null
                val name = o.optString("place_name", "").ifBlank {
                    o.optString("address_name", "(이름 없음)")
                }
                val addr = o.optString("road_address_name", "").ifBlank {
                    o.optString("address_name", "")
                }
                PlaceResult(name, addr, lat, lng)
            }
        }.getOrDefault(emptyList())
    }
}

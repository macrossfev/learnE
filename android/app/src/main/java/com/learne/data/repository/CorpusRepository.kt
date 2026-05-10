package com.learne.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.learne.data.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Config {
    const val BASE_URL = "http://macrossfev.diskstation.me:44000/learne"
}

class CorpusRepository(val context: Context) {

    private val gson = Gson()

    suspend fun loadWords(corpusId: String): List<Word> = withContext(Dispatchers.IO) {
        val cacheAge = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 // 7天

        // 尝试从 Room 缓存加载
        val db = com.learne.data.db.AppDatabase.getDatabase(context)
        val cached = db.corpusCacheDao().getByCorpusId(corpusId)
        if (cached != null && cached.timestamp > cacheAge) {
            android.util.Log.d("CorpusRepository", "Loading corpus from cache: $corpusId")
            return@withContext gson.fromJson<List<Word>>(cached.jsonData, object : com.google.gson.reflect.TypeToken<List<Word>>() {}.type) ?: emptyList()
        }

        // 从远程服务器加载
        val url = URL("${Config.BASE_URL}/corpora/$corpusId/data.json")
        android.util.Log.d("CorpusRepository", "Loading corpus: $corpusId from ${url.toString()}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000  // 15秒连接超时
        connection.readTimeout = 30000     // 30秒读取超时
        connection.instanceFollowRedirects = false

        val listType = object : TypeToken<List<Word>>() {}.type

        try {
            android.util.Log.d("CorpusRepository", "Connecting...")
            val responseCode = connection.responseCode
            android.util.Log.d("CorpusRepository", "Response: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode: 词库不存在或网络错误")
            }
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("CorpusRepository", "Data loaded: ${jsonString.length} bytes, starting Gson parse...")
            val result: List<Word> = gson.fromJson<List<Word>>(jsonString, listType) ?: emptyList()
            android.util.Log.d("CorpusRepository", "Gson parse complete: ${result.size} words, caching...")

            // 保存到 Room 缓存
            db.corpusCacheDao().insert(com.learne.data.model.CorpusCache(
                corpusId = corpusId,
                jsonData = jsonString,
                timestamp = System.currentTimeMillis()
            ))

            android.util.Log.d("CorpusRepository", "Cache saved, returning ${result.size} words...")
            return@withContext result
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("CorpusRepository", "Timeout: ${e.message}")
            // 超时且有缓存时回退到缓存
            if (cached != null) {
                android.util.Log.d("CorpusRepository", "Timeout, falling back to cache")
                return@withContext gson.fromJson<List<Word>>(cached.jsonData, listType) ?: emptyList()
            }
            throw Exception("网络超时，请检查网络连接")
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("CorpusRepository", "DNS error: ${e.message}")
            // 无网络且有缓存时回退到缓存
            if (cached != null) {
                android.util.Log.d("CorpusRepository", "No network, falling back to cache")
                return@withContext gson.fromJson<List<Word>>(cached.jsonData, listType) ?: emptyList()
            }
            throw Exception("无法连接服务器，请检查网络")
        } catch (e: Exception) {
            android.util.Log.e("CorpusRepository", "Error: ${e.message}")
            throw Exception("加载词库失败: ${e.message ?: "未知错误"}")
        } finally {
            connection.disconnect()
        }
    }

    fun getAudioPath(corpusId: String, word: String, type: String): String {
        val suffix = when (type) {
            "words" -> ""
            "meanings" -> "_meaning"
            "phrases" -> "_phrase"
            "phrase_meanings" -> "_phrase_meaning"
            "examples" -> "_example"
            "example_meanings" -> "_example_meaning"
            else -> ""
        }
        return "${Config.BASE_URL}/corpora/$corpusId/audio/$type/${word}${suffix}.mp3"
    }
}
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
        val url = URL("${Config.BASE_URL}/corpora/$corpusId/data.json")
        android.util.Log.d("CorpusRepository", "Loading corpus: $corpusId from ${url.toString()}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000  // 3秒连接超时
        connection.readTimeout = 5000     // 5秒读取超时
        connection.instanceFollowRedirects = false

        try {
            android.util.Log.d("CorpusRepository", "Connecting...")
            val responseCode = connection.responseCode
            android.util.Log.d("CorpusRepository", "Response: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode: 词库不存在或网络错误")
            }
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("CorpusRepository", "Data loaded: ${jsonString.length} bytes, starting Gson parse...")
            val listType = object : TypeToken<List<Word>>() {}.type
            val result: List<Word> = gson.fromJson<List<Word>>(jsonString, listType) ?: emptyList()
            android.util.Log.d("CorpusRepository", "Gson parse complete: ${result.size} words, returning...")
            return@withContext result
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("CorpusRepository", "Timeout: ${e.message}")
            throw Exception("网络超时，请检查网络连接")
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("CorpusRepository", "DNS error: ${e.message}")
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
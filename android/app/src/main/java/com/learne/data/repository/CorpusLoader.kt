package com.learne.data.repository

import com.learne.data.model.Corpus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object CorpusLoader {

    suspend fun loadCorpusList(): List<Corpus> = withContext(Dispatchers.IO) {
        val url = URL("${Config.BASE_URL}/corpora/list.json")
        android.util.Log.d("CorpusLoader", "Loading corpus list from ${url.toString()}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = false

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Corpus>>() {}.type
            val result: List<Corpus> = Gson().fromJson(jsonString, listType) ?: emptyList()
            return@withContext result
        } catch (e: Exception) {
            android.util.Log.e("CorpusLoader", "Failed to load corpus list: ${e.message}")
            return@withContext emptyList()
        } finally {
            connection.disconnect()
        }
    }
}

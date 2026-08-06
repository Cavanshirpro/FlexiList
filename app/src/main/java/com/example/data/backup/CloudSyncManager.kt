package com.example.data.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudSyncManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Public key-value bucket specific to FlexiList
    private val kvdbUrl = "https://kvdb.io/flexilist_sync_v2/"

    /**
     * Uploads the raw backup JSON to kvdb.io under the unique syncCode
     */
    suspend fun uploadToCloud(syncCode: String, jsonString: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonString.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(kvdbUrl + syncCode)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Downloads the raw backup JSON from kvdb.io for the unique syncCode
     */
    suspend fun downloadFromCloud(syncCode: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(kvdbUrl + syncCode)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        null
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }
}

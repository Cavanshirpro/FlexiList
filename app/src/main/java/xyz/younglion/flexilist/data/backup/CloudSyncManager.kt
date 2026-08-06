package xyz.younglion.flexilist.data.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class CloudSyncResult {
    data class Success(val syncCode: String) : CloudSyncResult()
    data class Error(val message: String) : CloudSyncResult()
}

class CloudSyncManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Public REST storage endpoint (JSONBlob Cloud Vault)
    private val jsonBlobUrl = "https://jsonblob.com/api/jsonBlob"

    /**
     * Uploads the raw backup JSON to JSONBlob cloud storage.
     * Returns CloudSyncResult.Success with the assigned syncCode or Error.
     */
    suspend fun uploadToCloud(syncCode: String, jsonString: String): CloudSyncResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonString.toRequestBody(mediaType)
                
                val cleanCode = syncCode.trim().removePrefix("FLX-").trim()

                if (cleanCode.length >= 8) {
                    // Try to update existing blob
                    val putRequest = Request.Builder()
                        .url("$jsonBlobUrl/$cleanCode")
                        .put(body)
                        .build()

                    try {
                        val putResponse = client.newCall(putRequest).execute()
                        if (putResponse.isSuccessful) {
                            putResponse.close()
                            return@withContext CloudSyncResult.Success(cleanCode)
                        }
                        putResponse.close()
                    } catch (e: Exception) {
                        // Fallback to creating a new blob below
                    }
                }

                // Create a new cloud blob on JSONBlob
                val postRequest = Request.Builder()
                    .url(jsonBlobUrl)
                    .post(body)
                    .build()

                val postResponse = client.newCall(postRequest).execute()
                if (postResponse.isSuccessful) {
                    val location = postResponse.header("Location")
                    val headerId = postResponse.header("X-jsonblob-id")
                    postResponse.close()

                    val newId = headerId ?: location?.substringAfterLast("/")
                    if (!newId.isNullOrBlank()) {
                        return@withContext CloudSyncResult.Success(newId)
                    }
                } else {
                    val errCode = postResponse.code
                    postResponse.close()
                    return@withContext CloudSyncResult.Error("Sunucu hatası ($errCode). Lütfen tekrar deneyin.")
                }

                CloudSyncResult.Error("Bulut sunucusundan geçerli bir yedek kimliği alınamadı.")
            } catch (e: IOException) {
                e.printStackTrace()
                CloudSyncResult.Error("Ağ Hatası: Bulut sunucusuna erişilemedi. İnternet bağlantınızı kontrol edin.")
            } catch (e: Exception) {
                e.printStackTrace()
                CloudSyncResult.Error("Beklenmeyen Hata: ${e.localizedMessage ?: "Yedekleme başarısız."}")
            }
        }
    }

    /**
     * Downloads the raw backup JSON from JSONBlob cloud storage for the syncCode
     */
    suspend fun downloadFromCloud(syncCode: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cleanCode = syncCode.trim().removePrefix("FLX-").trim()
                if (cleanCode.isBlank()) return@withContext null

                val request = Request.Builder()
                    .url("$jsonBlobUrl/$cleanCode")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}


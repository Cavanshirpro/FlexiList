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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Public REST storage endpoint (JSONBlob Cloud Vault)
    private val jsonBlobUrl = "https://jsonblob.com/api/jsonBlob"

    /**
     * Uploads the raw backup JSON to JSONBlob cloud storage.
     * Returns CloudSyncResult.Success with the assigned syncCode (FLX-XXXXXX) or Error.
     */
    suspend fun uploadToCloud(syncCode: String, jsonString: String): CloudSyncResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonString.toRequestBody(mediaType)
                
                val cleanCode = syncCode.trim().replaceFirst(Regex("^(?i)FLX-"), "").trim()

                if (cleanCode.length >= 6) {
                    // Try to update existing blob
                    val putRequest = Request.Builder()
                        .url("$jsonBlobUrl/$cleanCode")
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .put(body)
                        .build()

                    try {
                        val putResponse = client.newCall(putRequest).execute()
                        if (putResponse.isSuccessful) {
                            putResponse.close()
                            val formattedCode = if (cleanCode.startsWith("FLX-", ignoreCase = true)) cleanCode.uppercase() else "FLX-${cleanCode.uppercase()}"
                            return@withContext CloudSyncResult.Success(formattedCode)
                        }
                        putResponse.close()
                    } catch (e: Exception) {
                        // Fallback to creating a new blob below
                    }
                }

                // Create a new cloud blob on JSONBlob
                val postRequest = Request.Builder()
                    .url(jsonBlobUrl)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .post(body)
                    .build()

                val postResponse = client.newCall(postRequest).execute()
                if (postResponse.isSuccessful) {
                    val location = postResponse.header("Location")
                    val headerId = postResponse.header("X-jsonblob-id")
                    postResponse.close()

                    val newId = headerId ?: location?.substringAfterLast("/")
                    if (!newId.isNullOrBlank()) {
                        val formattedCode = "FLX-${newId.uppercase()}"
                        return@withContext CloudSyncResult.Success(formattedCode)
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
                val cleanCode = syncCode.trim().replaceFirst(Regex("^(?i)FLX-"), "").trim()
                if (cleanCode.isBlank()) return@withContext null

                val request = Request.Builder()
                    .url("$jsonBlobUrl/$cleanCode")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size().toLong() + count > 16 * 1024 * 1024) throw IOException("Backup exceeds import limit")
                                output.write(buffer, 0, count)
                            }
                            output.toString("UTF-8")
                        }
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


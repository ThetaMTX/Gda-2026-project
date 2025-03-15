package com.example.rasbberryaplication

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class ImageItem(
    val name: String,
    val path: String,
    val url: String
)

data class VideoItem(
    val name: String,
    val path: String
)

data class UploadResponse(
    val isSuccessful: Boolean,
    val path: String = "",
    val error: String = ""
)

data class ServerResponse(
    val isSuccessful: Boolean,
    val message: String = "",
    val error: String = ""
)

class TailscaleService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1)) // Force HTTP 1.1
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept", "image/*, */*")
                .header("Connection", "close") // Prevent keep-alive issues
                .build()
            chain.proceed(request)
        }
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private var baseUrl = "http://100.75.157.104:5000"

    suspend fun connect(host: String = "100.75.157.104", port: Int = 5000): Boolean {
        baseUrl = "http://$host:$port"
        return try {
            val response = makeRequest("api/status", "GET")
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getVideos(): List<VideoItem> {
        val response = makeRequest("api/videos", "GET")
        if (!response.isSuccessful) throw IOException("Failed to get videos")
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        try {
            val jsonArray = JSONArray(responseBody)
            return List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                VideoItem(
                    name = obj.getString("name"),
                    path = obj.getString("path")
                )
            }
        } catch (e: Exception) {
            throw IOException("Failed to parse video list: ${e.message}")
        }
    }

    suspend fun uploadVideo(file: File): UploadResponse {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "video",
                    file.name,
                    file.asRequestBody("video/*".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/videos/upload")
                .post(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val jsonResponse = JSONObject(responseBody)

            return if (response.isSuccessful) {
                UploadResponse(
                    isSuccessful = true,
                    path = jsonResponse.getString("path")
                )
            } else {
                UploadResponse(
                    isSuccessful = false,
                    error = jsonResponse.optString("error", "Unknown error")
                )
            }
        } catch (e: Exception) {
            return UploadResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    suspend fun playVideo(path: String, loop: Boolean = false) {
        try {
            val json = JSONObject().apply {
                put("path", path)
                put("loop", loop)
            }
            val response = makeRequest("api/video/play", "POST", json.toString())
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()
                val errorJson = try {
                    JSONObject(responseBody ?: "")
                } catch (e: Exception) {
                    null
                }
                val errorMessage = errorJson?.optString("error") ?: responseBody ?: "Unknown error"
                throw IOException("Failed to play video: $errorMessage")
            }
        } catch (e: Exception) {
            throw IOException("Failed to play video: ${e.message}")
        }
    }

    suspend fun playImage(path: String) {
        try {
            // First kill any existing processes
            val killCommand = JSONObject().apply {
                put("command", "kill_vlc")
            }
            
            // Send kill command first
            val killResponse = makeRequest("api/execute", "POST", killCommand.toString())
            if (!killResponse.isSuccessful) {
                val responseBody = killResponse.body?.string()
                val errorJson = try {
                    JSONObject(responseBody ?: "")
                } catch (e: Exception) {
                    null
                }
                val errorMessage = errorJson?.optString("error") ?: responseBody ?: "Unknown error"
                throw IOException("Failed to kill VLC processes: $errorMessage")
            }

            // Then play the image using VLC
            val json = JSONObject().apply {
                put("path", path)
            }
            val response = makeRequest("api/image/play", "POST", json.toString())
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()
                val errorJson = try {
                    JSONObject(responseBody ?: "")
                } catch (e: Exception) {
                    null
                }
                val errorMessage = errorJson?.optString("error") ?: responseBody ?: "Unknown error"
                throw IOException("Failed to display image: $errorMessage")
            }
        } catch (e: Exception) {
            throw IOException("Failed to display image: ${e.message}")
        }
    }

    suspend fun clearScreen(): ServerResponse {
        return try {
            // First try to kill VLC processes
            val killCommand = JSONObject().apply {
                put("command", "kill_vlc")
            }
            
            // Send kill command first
            val killResponse = makeRequest("api/execute", "POST", killCommand.toString())
            if (!killResponse.isSuccessful) {
                val responseBody = killResponse.body?.string()
                val errorJson = try {
                    JSONObject(responseBody ?: "")
                } catch (e: Exception) {
                    null
                }
                val errorMessage = errorJson?.optString("error") ?: responseBody ?: "Unknown error"
                return ServerResponse(
                    isSuccessful = false,
                    error = "Failed to kill VLC processes: $errorMessage"
                )
            }

            // Then clear the screen
            val response = makeRequest("api/screen/clear", "POST")
            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val jsonResponse = JSONObject(responseBody)
            
            if (response.isSuccessful) {
                ServerResponse(
                    isSuccessful = true,
                    message = "Screen cleared and VLC processes terminated"
                )
            } else {
                ServerResponse(
                    isSuccessful = false,
                    error = jsonResponse.optString("error", "Failed to clear screen")
                )
            }
        } catch (e: Exception) {
            ServerResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    suspend fun activateScreensaver(): ServerResponse {
        return try {
            val response = makeRequest("api/screensaver", "POST")
            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val jsonResponse = JSONObject(responseBody)
            
            if (response.isSuccessful) {
                ServerResponse(
                    isSuccessful = true,
                    message = jsonResponse.optString("message", "Screensaver activated")
                )
            } else {
                ServerResponse(
                    isSuccessful = false,
                    error = jsonResponse.optString("error", "Failed to activate screensaver")
                )
            }
        } catch (e: Exception) {
            ServerResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    suspend fun getImages(): List<ImageItem> {
        val response = makeRequest("api/images", "GET")
        if (!response.isSuccessful) throw IOException("Failed to get images")
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        try {
            val jsonArray = JSONArray(responseBody)
            return List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val path = obj.getString("path")
                // Construct the full URL for the image
                val url = "$baseUrl/api/images/$name"
                ImageItem(
                    name = name,
                    path = path,
                    url = url
                )
            }
        } catch (e: Exception) {
            throw IOException("Failed to parse image list: ${e.message}")
        }
    }

    suspend fun uploadImage(file: File): UploadResponse {
        try {
            // Determine MIME type based on file extension
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> "image/jpeg" // default to JPEG
            }

            val imageBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", file.name, imageBody)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/images/upload")
                .post(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val jsonResponse = JSONObject(responseBody)

            return if (response.isSuccessful) {
                UploadResponse(
                    isSuccessful = true,
                    path = jsonResponse.getString("path")
                )
            } else {
                UploadResponse(
                    isSuccessful = false,
                    error = jsonResponse.optString("error", "Unknown error")
                )
            }
        } catch (e: Exception) {
            return UploadResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private suspend fun makeRequest(endpoint: String, method: String, jsonBody: String? = null): Response {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/$endpoint")
                .method(
                    method,
                    if (jsonBody != null) jsonBody.toRequestBody(JSON) else null
                )
                .build()

            client.newCall(request).execute()
        }
    }

    companion object {
        const val COMMAND_PLAY_VIDEO = "PLAY_VIDEO"
        const val COMMAND_CLEAR_SCREEN = "CLEAR_SCREEN"
        const val COMMAND_SCREENSAVER = "SCREENSAVER"
    }
} 
package com.objectcounter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val MODEL_URL =
        "https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n_saved_model.tflite"
    private const val MODEL_FILE = "yolov8n.tflite"

    fun isModelDownloaded(context: Context): Boolean {
        return File(context.filesDir, MODEL_FILE).exists()
    }

    suspend fun downloadModel(
        context: Context,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val fileLength = connection.contentLength
            val outputFile = File(context.filesDir, MODEL_FILE)

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var bytes: Int

                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (fileLength > 0) {
                            val progress = (downloaded * 100 / fileLength).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

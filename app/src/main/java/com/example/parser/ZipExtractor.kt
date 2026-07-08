package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractionProgress(
    val stage: String, // "detecting", "extracting", "searching", "completed"
    val percentage: Float, // 0.0 to 1.0
    val bytesProcessed: Long,
    val totalBytes: Long,
    val elapsedTimeMs: Long,
    val estimatedRemainingTimeMs: Long
)

data class ExtractedMediaInfo(
    val fileName: String,
    val localPath: String,
    val fileSize: Long,
    val fileType: String, // "IMAGE", "VIDEO", "AUDIO", "VOICE", "DOCUMENT", "STICKER", "GIF"
    val timestamp: Long
)

data class ExtractionResult(
    val chatFileUri: Uri?,
    val chatFilePath: String?,
    val chatFileText: String,
    val chatFileType: String, // "WHATSAPP", "TELEGRAM", "GENERIC", "UNSUPPORTED"
    val fileHash: String,
    val mediaFiles: List<ExtractedMediaInfo>,
    val tempDir: String
)

object ZipExtractor {
    private const val TAG = "ZipExtractor"

    fun extractZipArchive(
        context: Context,
        zipUri: Uri,
        sessionId: String,
        onProgress: (progress: ExtractionProgress) -> Unit
    ): ExtractionResult {
        val startTime = System.currentTimeMillis()
        val contentResolver = context.contentResolver

        // 1. Get total file size of the ZIP for progress
        var totalBytes = 0L
        try {
            contentResolver.query(zipUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    totalBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get zip file size", e)
        }
        if (totalBytes <= 0) {
            totalBytes = 100 * 1024 * 1024 // Fallback 100MB if size query fails
        }

        onProgress(ExtractionProgress("detecting", 0f, 0L, totalBytes, 0L, -1L))

        // 2. Setup secure target directory
        val baseTempDir = File(context.cacheDir, "extracted_chats")
        if (!baseTempDir.exists()) baseTempDir.mkdirs()
        
        val sessionTempDir = File(baseTempDir, sessionId)
        if (sessionTempDir.exists()) {
            sessionTempDir.deleteRecursively()
        }
        sessionTempDir.mkdirs()

        val mediaFiles = mutableListOf<ExtractedMediaInfo>()
        var chatFile: File? = null
        var bytesProcessed = 0L

        // 3. Extract the ZIP streaming to handle >2GB files
        val buffer = ByteArray(1024 * 64) // 64KB buffer
        var inputStream = contentResolver.openInputStream(zipUri) ?: throw IOException("Could not open source stream")
        val zipStream = ZipInputStream(BufferedInputStream(inputStream))

        try {
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Prevent Zip Slip / directory traversal attack
                    val targetFile = File(sessionTempDir, entry.name)
                    if (!targetFile.canonicalPath.startsWith(sessionTempDir.canonicalPath)) {
                        throw SecurityException("Security risk: Zip Entry attempts traversal: ${entry.name}")
                    }

                    // Create parent folders if nested
                    targetFile.parentFile?.mkdirs()

                    // Extract and measure bytes
                    val outputStream = FileOutputStream(targetFile)
                    var read: Int
                    while (zipStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesProcessed += read

                        // Throttle progress triggers
                        val elapsed = System.currentTimeMillis() - startTime
                        val pct = (bytesProcessed.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                        val speed = if (elapsed > 100) bytesProcessed.toDouble() / elapsed else 0.0
                        val estRemaining = if (speed > 0) ((totalBytes - bytesProcessed) / speed).toLong() else -1L

                        onProgress(
                            ExtractionProgress(
                                stage = "extracting",
                                percentage = pct,
                                bytesProcessed = bytesProcessed,
                                totalBytes = totalBytes,
                                elapsedTimeMs = elapsed,
                                estimatedRemainingTimeMs = estRemaining
                            )
                        )
                    }
                    outputStream.close()

                    // Keep track of files
                    val extension = targetFile.extension.lowercase()
                    if (extension == "txt") {
                        // Usually WhatsApp is either '_chat.txt' or 'WhatsApp Chat with xxx.txt'
                        if (chatFile == null || targetFile.name.contains("whatsapp", ignoreCase = true) || !targetFile.name.startsWith("._")) {
                            chatFile = targetFile
                        }
                    } else {
                        val fileType = classifyFileExtension(extension)
                        if (fileType != "UNKNOWN") {
                            mediaFiles.add(
                                ExtractedMediaInfo(
                                    fileName = targetFile.name,
                                    localPath = targetFile.absolutePath,
                                    fileSize = targetFile.length(),
                                    fileType = fileType,
                                    timestamp = targetFile.lastModified()
                                )
                            )
                        }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip entry", e)
            // Handle corruption or other read exceptions
            throw e
        } finally {
            try {
                zipStream.close()
                inputStream.close()
            } catch (ignored: Exception) {}
        }

        // 4. Recursively scan the directory if no chat file was found at top level
        onProgress(ExtractionProgress("searching", 0.95f, bytesProcessed, totalBytes, System.currentTimeMillis() - startTime, 0L))
        if (chatFile == null) {
            val scanResult = searchForTxtFile(sessionTempDir)
            chatFile = scanResult
        }

        if (chatFile == null) {
            throw FileNotFoundException("Could not find any valid text chat file (.txt) in the exported archive.")
        }

        // 5. Read chat contents
        val chatTextBuilder = java.lang.StringBuilder()
        // Explicitly use UTF-8 to handle all multilingual and emoji characters safely
        val reader = BufferedReader(InputStreamReader(FileInputStream(chatFile), Charsets.UTF_8))
        var line: String? = reader.readLine()
        while (line != null) { 
            chatTextBuilder.append(line).append("\n")
            line = reader.readLine()
        }
        reader.close()

        val rawText = chatTextBuilder.toString()
        val chatType = classifyChatText(rawText)

        // 6. Compute Hash of the text file for duplicate import detection
        val fileHash = computeFileHash(chatFile)

        onProgress(ExtractionProgress("completed", 1.0f, totalBytes, totalBytes, System.currentTimeMillis() - startTime, 0L))

        return ExtractionResult(
            chatFileUri = Uri.fromFile(chatFile),
            chatFilePath = chatFile.absolutePath,
            chatFileText = rawText,
            chatFileType = chatType,
            fileHash = fileHash,
            mediaFiles = mediaFiles,
            tempDir = sessionTempDir.absolutePath
        )
    }

    private fun searchForTxtFile(directory: File): File? {
        val files = directory.listFiles() ?: return null
        for (file in files) {
            if (file.isDirectory) {
                val found = searchForTxtFile(file)
                if (found != null) return found
            } else if (file.extension.lowercase() == "txt" && !file.name.startsWith("._")) {
                // Ignore system hidden files like ._chat.txt
                return file
            }
        }
        return null
    }

    fun classifyFileExtension(ext: String): String {
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "bmp" -> "IMAGE"
            "mp4", "3gp", "mkv", "avi", "mov", "webm" -> "VIDEO"
            "mp3", "m4a", "wav", "aac", "flac" -> "AUDIO"
            "opus", "ogg" -> "VOICE"
            "pdf", "doc", "docx", "xls", "ppt", "pptx", "rtf", "apk", "zip", "txt", "json" -> "DOCUMENT"
            "csv", "vcf", "xlsx" -> "MEMBER_LIST"
            "tgs" -> "STICKER"
            "gif" -> "GIF"
            else -> "UNKNOWN"
        }
    }

    fun classifyChatText(text: String): String {
        if (text.isBlank()) return "UNSUPPORTED"
        
        val lines = text.lines().take(50)
        var whatsappScore = 0
        var telegramScore = 0

        // WhatsApp typical pattern: [25/06/2026, 09:30] User: text or 25/06/2026, 09:30 - User: text
        val waPattern = Regex("^[\\[]?\\d{1,4}[/\\-.]\\d{1,4}[/\\-.]\\d{1,4}[, ]+\\d{1,2}:\\d{2}")
        // Telegram typical pattern: [30.06.26 15:30:12] User: text or Name, [dd.MM.yy HH:mm]
        val tgPattern = Regex("^[\\[]\\d{2}\\.\\d{2}\\.\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[\\]]")

        for (line in lines) {
            if (line.isBlank()) continue
            if (waPattern.containsMatchIn(line)) {
                whatsappScore++
            }
            if (tgPattern.containsMatchIn(line)) {
                telegramScore++
            }
            if (line.contains("Telegram chat export", ignoreCase = true)) {
                telegramScore += 10
            }
        }

        return when {
            whatsappScore > 3 -> "WHATSAPP"
            telegramScore > 3 -> "TELEGRAM"
            text.contains(":", ignoreCase = true) && text.length > 50 -> "GENERIC"
            else -> "UNSUPPORTED"
        }
    }

    private fun computeFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192)
            var count: Int
            while (fis.read(buffer).also { count = it } != -1) {
                digest.update(buffer, 0, count)
            }
            fis.close()
            val hashBytes = digest.digest()
            val sb = java.lang.StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Hash computation failed", e)
            file.name + "_" + file.length() + "_" + file.lastModified()
        }
    }
}

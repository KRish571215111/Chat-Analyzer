package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ai.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ClassificationResult(
    val detectedType: String,
    val confidence: Float,
    val reason: String,
    val isConfident: Boolean
)

data class ImportedMember(
    val originalName: String,
    val originalPhone: String?,
    val normalizedPhone: String?
)

object MemberImportEngine {
    
    fun normalizePhone(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        // Remove spaces, tabs, formatting characters (like non-breaking spaces), and standard characters like (), -, .
        var normalized = phone.replace(Regex("[\\s\\t()\\-.\\u00a0\\u2007\\u200f\\u200e]"), "")
        // Retain only digits (strip leading + and anything else)
        normalized = normalized.replace(Regex("[^\\d]"), "")
        
        if (normalized.startsWith("00")) {
            if (normalized.length > 2) {
                normalized = normalized.substring(2)
            } else {
                normalized = ""
            }
        }
        if (normalized.startsWith("0")) {
            if (normalized.length > 1) {
                normalized = normalized.substring(1)
            } else {
                normalized = ""
            }
        }
        // Convert 10 digits to 12-digit Indian format (+91/91 prefix)
        if (normalized.length == 10) {
            normalized = "91$normalized"
        }
        if (normalized.length < 7) return null
        return normalized.takeIf { it.isNotBlank() }
    }

    suspend fun classifyContent(context: Context, uri: Uri, aiService: AiService?): ClassificationResult = withContext(Dispatchers.IO) {
        // Stage 1: Basic Validation
        val name = getFileName(context, uri).lowercase()
        val type = context.contentResolver.getType(uri) ?: ""
        
        // Stage 2, 3, 4: Read File, Detect Encoding, Extract Content
        val content = readFirstLines(context, uri, 100)
        
        // Stage 5: Local File Classification
        var detectedType = "Unknown"
        var confidence = 0.0f
        var reason = "No recognizable patterns found."

        if (name.endsWith(".json") || type.contains("json") || (content.trimStart().startsWith("[") && content.contains("{"))) {
            detectedType = "JSON Contact List"
            confidence = 0.95f
            reason = "JSON array structure detected."
        } else if (name.endsWith(".vcf") || type.contains("vcard") || content.contains("BEGIN:VCARD")) {
            detectedType = "VCF Contacts"
            confidence = 1.0f
            reason = "Standard VCard header detected."
        } else if (name.endsWith(".csv") || type.contains("csv") || content.contains(",")) {
            val hasPhone = Regex("\\+?\\d{7,15}").containsMatchIn(content)
            val hasNames = content.lowercase().contains("name") || content.lowercase().contains("first")
            if (hasPhone) {
                detectedType = "WhatsApp Community Member List"
                confidence = 0.85f
                reason = "Delimited format with phone numbers detected."
            } else if (hasNames) {
                detectedType = "CSV Contact List"
                confidence = 0.7f
                reason = "Delimited format with names but no clear phone numbers."
            } else {
                detectedType = "Random CSV"
                confidence = 0.5f
                reason = "Delimited format without clear contact signatures."
            }
        } else if (name.endsWith(".xlsx") || type.contains("spreadsheet")) {
            detectedType = "Excel Contact List"
            confidence = 0.8f
            reason = "Excel extension detected."
        } else if (Regex("\\+?\\d{7,15}").containsMatchIn(content)) {
            detectedType = "TXT Contact List"
            confidence = 0.8f
            reason = "Text file containing phone numbers."
        }

        // Stage 6: If confidence is LOW, use the configured Z.AI API
        if (confidence < 0.90f && aiService != null && content.isNotBlank()) {
            try {
                val prompt = """
                    Classify the following text into one of these exact categories:
                    WhatsApp Export, WhatsApp Community Member List, CSV Contact List, VCF Contacts, TXT Contact List, JSON Contact List, Excel Contact List, Media Archive, Random CSV, Unknown.
                    
                    Return ONLY a JSON object:
                    {"Detected Type": "string", "Confidence": 0.0 to 1.0, "Reason": "string"}
                    
                    Text Sample:
                    ${content.take(1000)}
                """.trimIndent()
                
                val aiResult = aiService.generateAnalysis(prompt, emptyList())
                val jsonMatch = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(aiResult)
                if (jsonMatch != null) {
                    val json = JSONObject(jsonMatch.value)
                    val aiType = json.optString("Detected Type", detectedType)
                    val aiConf = json.optDouble("Confidence", confidence.toDouble()).toFloat()
                    val aiReason = json.optString("Reason", reason)
                    
                    val validTypes = listOf("WhatsApp Export", "WhatsApp Community Member List", "CSV Contact List", "VCF Contacts", "TXT Contact List", "JSON Contact List", "Excel Contact List", "Media Archive", "Random CSV", "Unknown")
                    if (validTypes.contains(aiType)) {
                        detectedType = aiType
                        confidence = aiConf
                        reason = aiReason
                    }
                }
            } catch (e: Exception) {
                Log.e("MemberImportEngine", "AI Classification failed", e)
            }
        }

        ClassificationResult(detectedType, confidence, reason, confidence >= 0.90f)
    }

    suspend fun parseFile(context: Context, uri: Uri): List<ImportedMember> = withContext(Dispatchers.IO) {
        val name = getFileName(context, uri).lowercase()
        val type = context.contentResolver.getType(uri) ?: ""
        
        return@withContext when {
            name.endsWith(".json") || type.contains("json") -> parseJson(context, uri)
            name.endsWith(".vcf") || type.contains("vcard") -> parseVcf(context, uri)
            name.endsWith(".csv") || type.contains("csv") -> parseCsv(context, uri)
            name.endsWith(".xlsx") || type.contains("spreadsheet") -> parseXlsx(context, uri)
            name.endsWith(".txt") || type.contains("text") -> parseTxt(context, uri)
            else -> {
                val content = readFirstLines(context, uri, 5)
                when {
                    content.trimStart().startsWith("[") -> parseJson(context, uri)
                    content.contains("BEGIN:VCARD") -> parseVcf(context, uri)
                    content.contains(",") -> parseCsv(context, uri)
                    else -> parseTxt(context, uri) 
                }
            }
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/') ?: ""
    }

    private fun readFirstLines(context: Context, uri: Uri, lines: Int): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val sb = java.lang.StringBuilder()
            for (i in 0 until lines) {
                val line = reader.readLine() ?: break
                sb.append(line).append("\n")
            }
            sb.toString()
        } ?: ""
    }

    private fun parseJson(context: Context, uri: Uri): List<ImportedMember> {
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return emptyList()
        val members = mutableListOf<ImportedMember>()
        try {
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", obj.optString("Name", obj.optString("display_name", "")))
                val phone = obj.optString("phone", obj.optString("Phone", obj.optString("mobile", "")))
                if (name.isNotBlank() || phone.isNotBlank()) {
                    members.add(ImportedMember(name, phone.takeIf { it.isNotBlank() }, normalizePhone(phone)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return members
    }

    private fun parseVcf(context: Context, uri: Uri): List<ImportedMember> {
        val members = mutableListOf<ImportedMember>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var currentName = ""
            var currentPhone = ""
            var inVcard = false
            
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed == "BEGIN:VCARD") {
                    inVcard = true
                    currentName = ""
                    currentPhone = ""
                } else if (trimmed == "END:VCARD") {
                    inVcard = false
                    if (currentName.isNotBlank() || currentPhone.isNotBlank()) {
                        members.add(ImportedMember(currentName, currentPhone.takeIf { it.isNotBlank() }, normalizePhone(currentPhone)))
                    }
                } else if (inVcard) {
                    if (trimmed.startsWith("FN:")) {
                        currentName = trimmed.substringAfter("FN:")
                    } else if (trimmed.startsWith("TEL") && trimmed.contains(":")) {
                        val phone = trimmed.substringAfter(":")
                        if (currentPhone.isBlank()) {
                            currentPhone = phone
                        }
                    }
                }
            }
        }
        return members
    }

    private fun looksLikePhone(str: String): Boolean {
        val clean = str.replace(Regex("[\\s\\t()\\-.\\u00a0\\u2007\\u200f\\u200e]"), "").replace("+", "")
        return clean.all { it.isDigit() } && clean.length in 7..15
    }

    private fun parseCsv(context: Context, uri: Uri): List<ImportedMember> {
        val members = mutableListOf<ImportedMember>()
        try {
            var content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return emptyList()
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1)
            }
            
            val lines = content.split(Regex("\\r?\\n|\\r")).filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()
            
            // Detect separator based on frequency in the first line
            val firstLine = lines[0]
            val separator = when {
                firstLine.contains("\t") -> "\t"
                firstLine.contains(";") -> ";"
                firstLine.contains("|") -> "|"
                else -> ","
            }
            
            val firstRowCols = parseCsvLine(firstLine, separator)
            
            // Check if first row is a header
            var isHeader = false
            var nameIndex = -1
            var phoneIndex = -1
            
            for ((index, col) in firstRowCols.withIndex()) {
                val c = col.lowercase()
                if (c.contains("phone") || c.contains("mobile") || c.contains("contact") || c.contains("number") || c.contains("participant") || c.contains("member")) {
                    phoneIndex = index
                    isHeader = true
                } else if (c.contains("name") || c.contains("first") || c.contains("last") || c.contains("fullname") || c.contains("display_name")) {
                    nameIndex = index
                    isHeader = true
                }
            }
            
            val startIdx = 0
            
            for (i in startIdx until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue
                val columns = parseCsvLine(line, separator)
                if (columns.isEmpty()) continue
                
                var name = ""
                var phone = ""
                
                if (isHeader) {
                    if (nameIndex != -1 && nameIndex < columns.size) {
                        name = columns[nameIndex].trim()
                    }
                    if (phoneIndex != -1 && phoneIndex < columns.size) {
                        phone = columns[phoneIndex].trim()
                    }
                    
                    // Fallbacks if one of them was not explicitly indexed
                    if (nameIndex == -1 || phoneIndex == -1) {
                        if (columns.size == 1) {
                            val single = columns[0].trim()
                            if (looksLikePhone(single)) {
                                phone = single
                            } else {
                                name = single
                            }
                        } else if (columns.size >= 2) {
                            if (nameIndex == -1 && phoneIndex != -1) {
                                // Find any non-phone column as name
                                for (colIdx in columns.indices) {
                                    if (colIdx != phoneIndex && columns[colIdx].isNotBlank()) {
                                        name = columns[colIdx].trim()
                                        break
                                    }
                                }
                            } else if (phoneIndex == -1 && nameIndex != -1) {
                                // Find any phone-like column as phone
                                for (colIdx in columns.indices) {
                                    if (colIdx != nameIndex && looksLikePhone(columns[colIdx])) {
                                        phone = columns[colIdx].trim()
                                        break
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // No header
                    if (columns.size == 1) {
                        val single = columns[0].trim()
                        if (looksLikePhone(single)) {
                            phone = single
                        } else {
                            name = single
                        }
                    } else if (columns.size >= 2) {
                        val col0 = columns[0].trim()
                        val col1 = columns[1].trim()
                        val looks0 = looksLikePhone(col0)
                        val looks1 = looksLikePhone(col1)
                        if (looks0 && !looks1) {
                            phone = col0
                            name = col1
                        } else if (looks1 && !looks0) {
                            phone = col1
                            name = col0
                        } else {
                            // Default mapping: col 0 is name, col 1 is phone
                            name = col0
                            phone = col1
                        }
                    } else {
                        // More columns, find the first phone-like column and first non-phone-like column
                        for (col in columns) {
                            val trimmed = col.trim()
                            if (looksLikePhone(trimmed)) {
                                if (phone.isBlank()) phone = trimmed
                            } else if (trimmed.isNotBlank()) {
                                if (name.isBlank()) name = trimmed
                            }
                        }
                    }
                }
                
                if (name.isNotBlank() || phone.isNotBlank()) {
                    members.add(ImportedMember(name, phone.takeIf { it.isNotBlank() }, normalizePhone(phone)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return members
    }

    private fun parseCsvLine(line: String, separator: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            if (char == '\"') {
                inQuotes = !inQuotes
            } else if (line.startsWith(separator, i) && !inQuotes) {
                result.add(current.toString().trim().removeSurrounding("\""))
                current.clear()
                i += separator.length - 1
            } else {
                current.append(char)
            }
            i++
        }
        result.add(current.toString().trim().removeSurrounding("\""))
        return result
    }

    private fun parseTxt(context: Context, uri: Uri): List<ImportedMember> {
        val members = mutableListOf<ImportedMember>()
        val phoneRegex = Regex("[+\\d\\s\\-()]+")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                
                val parts = trimmed.split(Regex("[,;\\-|\t]+"))
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val phone = parts[1].trim()
                    if (phone.matches(phoneRegex) || phone.replace(Regex("[^\\d+]"), "").length >= 7) {
                        members.add(ImportedMember(name, phone, normalizePhone(phone)))
                        return@forEachLine
                    } else if (name.matches(phoneRegex) || name.replace(Regex("[^\\d+]"), "").length >= 7) {
                        members.add(ImportedMember(phone, name, normalizePhone(name)))
                        return@forEachLine
                    } else if ((name.lowercase().contains("name") || name.lowercase().contains("phone")) && (phone.lowercase().contains("name") || phone.lowercase().contains("phone") || phone.lowercase().contains("number"))) {
                        members.add(ImportedMember(name, phone, null))
                        return@forEachLine
                    }
                }
                
                val digitsOnly = trimmed.replace(Regex("[^\\d+]"), "")
                if ((trimmed.matches(phoneRegex) || digitsOnly.length >= 7) && digitsOnly.length < 20) {
                    members.add(ImportedMember("", trimmed, normalizePhone(trimmed)))
                } else {
                    members.add(ImportedMember(trimmed, null, null))
                }
            }
        }
        return members
    }

    private fun parseXlsx(context: Context, uri: Uri): List<ImportedMember> {
        val members = mutableListOf<ImportedMember>()
        try {
            val sharedStrings = mutableListOf<String>()
            val sheetData = mutableListOf<List<String>>()
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        val content = zip.bufferedReader(Charsets.UTF_8).readText()
                        val tRegex = Regex("<t.*?>(.*?)</t>")
                        val matches = tRegex.findAll(content)
                        for (match in matches) {
                            sharedStrings.add(match.groupValues[1])
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/worksheets/sheet1.xml") {
                        val content = zip.bufferedReader(Charsets.UTF_8).readText()
                        val rowRegex = Regex("<row.*?>(.*?)</row>")
                        val cRegex = Regex("<c.*?r=\"([A-Z]+)(\\d+)\".*?(t=\"s\")?.*?><v>(.*?)</v></c>")
                        
                        val rows = rowRegex.findAll(content)
                        for (rowMatch in rows) {
                            val rowContent = rowMatch.groupValues[1]
                            val cols = cRegex.findAll(rowContent)
                            val rowValues = mutableListOf<String>()
                            var lastColIndex = 0
                            for (colMatch in cols) {
                                val colName = colMatch.groupValues[1]
                                val colIndex = colNameToIndex(colName)
                                while (lastColIndex < colIndex) {
                                    rowValues.add("")
                                    lastColIndex++
                                }
                                val isString = colMatch.groupValues[3] == "t=\"s\""
                                val value = colMatch.groupValues[4]
                                val resolvedValue = if (isString) {
                                    val idx = value.toIntOrNull()
                                    if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else value
                                } else {
                                    value
                                }
                                rowValues.add(resolvedValue)
                                lastColIndex++
                            }
                            if (rowValues.isNotEmpty()) {
                                sheetData.add(rowValues)
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            
            if (sheetData.isNotEmpty()) {
                val phoneRegex = Regex("\\+?\\d{7,15}")
                for (i in 0 until sheetData.size) {
                    val row = sheetData[i]
                    var name = ""
                    var phone = ""
                    
                    for (col in row) {
                        val trimmed = col.trim()
                        if (trimmed.matches(phoneRegex) || trimmed.replace(Regex("[^\\d+]"), "").length >= 7) {
                            if (phone.isBlank()) phone = trimmed
                        } else if (trimmed.isNotBlank() && !trimmed.lowercase().contains("name") && !trimmed.lowercase().contains("phone")) {
                            if (name.isBlank()) name = trimmed
                        }
                    }
                    
                    if (name.isNotBlank() || phone.isNotBlank()) {
                        val lowerName = name.lowercase()
                        val lowerPhone = phone.lowercase()
                        members.add(ImportedMember(name, phone.takeIf { it.isNotBlank() }, normalizePhone(phone)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return members
    }
    
    private fun colNameToIndex(colName: String): Int {
        var index = 0
        for (i in colName.indices) {
            index = index * 26 + (colName[i] - 'A' + 1)
        }
        return index - 1
    }
}

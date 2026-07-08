package com.example.parser

import com.example.data.ChatMessage
import com.example.data.Participant
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object WhatsAppParser {

    // Regex to match typical WhatsApp timestamps:
    // Support formats like:
    // 30/06/2026, 15:30 - John Doe: Hello
    // 30/06/26, 3:30 pm - John Doe: Hello
    // [30/06/2026, 15:30:12] John Doe: Hello
    // 30.06.26, 15:30: John Doe: Hello
    private val timestampRegex = Regex(
        "^[\\[]?(\\d{1,4}[/\\-.]\\d{1,4}[/\\-.]\\d{1,4})[, ]+(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[aApP][mM])?)[\\]]?[\\s\\-:]+\\s*"
    )

    // Common Date formats in WhatsApp exports
    private val dateFormats = listOf(
        "dd/MM/yyyy, HH:mm",
        "dd/MM/yy, HH:mm",
        "MM/dd/yyyy, HH:mm",
        "MM/dd/yy, HH:mm",
        "dd/MM/yyyy, h:mm a",
        "dd/MM/yy, h:mm a",
        "MM/dd/yyyy, h:mm a",
        "MM/dd/yy, h:mm a",
        "yyyy-MM-dd, HH:mm",
        "yyyy/MM/dd, HH:mm",
        "dd.MM.yy, HH:mm",
        "dd.MM.yyyy, HH:mm"
    )

    data class ParsedMessage(
        val timestamp: Long,
        val sender: String,
        val text: String,
        val rawText: String,
        val isSystem: Boolean
    )

    fun parseChatExport(
        lines: List<String>, 
        sessionId: Long,
        onProgress: ((progress: Float, parsedCount: Int, totalLines: Int) -> Unit)? = null
    ): ParserResult {
        if (lines.isEmpty()) {
            return ParserResult(emptyList(), emptyList(), 0, 0L, 0L)
        }

        val parsedMessages = mutableListOf<ParsedMessage>()
        var currentMessage: ParsedMessage? = null
        var currentBodyBuilder = java.lang.StringBuilder()
        var currentRawBodyBuilder = java.lang.StringBuilder()

        val totalLines = lines.size
        var lastReportTime = System.currentTimeMillis()

        for ((index, line) in lines.withIndex()) {
            if (line.isBlank()) continue

            // Report progress every 100ms
            if (onProgress != null && index % 1000 == 0) {
                val now = System.currentTimeMillis()
                if (now - lastReportTime > 100) {
                    lastReportTime = now
                    onProgress(index.toFloat() / totalLines, parsedMessages.size, totalLines)
                }
            }

            val match = timestampRegex.find(line)
            if (match != null) {
                // If we were building a previous message, save it first
                if (currentMessage != null) {
                    parsedMessages.add(currentMessage.copy(
                        text = currentBodyBuilder.toString().trim(),
                        rawText = currentRawBodyBuilder.toString()
                    ))
                }

                val dateStr = match.groupValues[1]
                val timeStr = match.groupValues[2]
                val fullTimestamp = parseTimestamp(dateStr, timeStr)

                // Text after the timestamp match
                val remainder = line.substring(match.range.last + 1)

                // Check if it's Sender: Message or a System Event
                // A sender line typically has ": " separating the sender name and message
                val colonIndex = remainder.indexOf(": ")
                if (colonIndex != -1) {
                    val rawSender = remainder.substring(0, colonIndex)
                    val rawMessageText = remainder.substring(colonIndex + 2)
                    
                    val sender = normalizeSender(rawSender)
                    val messageText = normalizeWhitespace(rawMessageText)
                    
                    currentMessage = ParsedMessage(
                        timestamp = fullTimestamp,
                        sender = sender,
                        text = messageText,
                        rawText = rawMessageText,
                        isSystem = false
                    )
                    currentBodyBuilder = java.lang.StringBuilder(messageText)
                    currentRawBodyBuilder = java.lang.StringBuilder(rawMessageText)
                } else {
                    // System event (e.g., "John Doe added Jane", "You created group...")
                    val systemText = normalizeWhitespace(remainder)
                    currentMessage = ParsedMessage(
                        timestamp = fullTimestamp,
                        sender = "System",
                        text = systemText,
                        rawText = remainder,
                        isSystem = true
                    )
                    currentBodyBuilder = java.lang.StringBuilder(systemText)
                    currentRawBodyBuilder = java.lang.StringBuilder(remainder)
                }
            } else {
                // Continuation of multiline message
                if (currentMessage != null) {
                    currentBodyBuilder.append("\n").append(line)
                    currentRawBodyBuilder.append("\n").append(line)
                }
            }
        }

        // Add the last message
        if (currentMessage != null) {
            parsedMessages.add(currentMessage.copy(
                text = currentBodyBuilder.toString().trim(),
                rawText = currentRawBodyBuilder.toString()
            ))
        }

        if (parsedMessages.isEmpty()) {
            return ParserResult(emptyList(), emptyList(), 0, 0L, 0L)
        }

        // Convert ParsedMessages to Room Entity ChatMessage and compute Participant Stats
        val chatMessages = mutableListOf<ChatMessage>()
        val participantsMap = mutableMapOf<String, ParticipantStats>()
        val globalEmojiFreq = mutableMapOf<String, Int>()
        val globalLinks = mutableListOf<ParsedLink>()

        var dateStart = Long.MAX_VALUE
        var dateEnd = Long.MIN_VALUE
        
        // 1. Identity Resolution
        val originalSenders = parsedMessages.filter { !it.isSystem }.map { it.sender }.distinct()
        
        val phoneRegex = Regex("^[+\\d\\s\\-()\\u202F~]+$")
        val senderToCanonical = mutableMapOf<String, String>()
        val canonicalToNormalized = mutableMapOf<String, String>()
        
        // Let's use normalized phone as the primary grouping key for EVERY sender that has one
        for (sender in originalSenders) {
            val normalized = com.example.parser.MemberImportEngine.normalizePhone(sender)
            if (normalized != null && normalized.length >= 7) {
                canonicalToNormalized[sender] = normalized
            }
        }
        
        // Group by normalized phone
        val normalizedGroups = canonicalToNormalized.entries.groupBy({ it.value }, { it.key })
        
        for ((normalized, group) in normalizedGroups) {
            // Pick a canonical name for this normalized phone.
            // Prefer one that doesn't just look like a phone number, if available.
            val canonical = group.firstOrNull { 
                val clean = it.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0\\u202F]"), "")
                !(clean.length >= 7 && phoneRegex.matches(clean))
            } ?: group.first()
            
            for (sender in group) {
                senderToCanonical[sender] = canonical
            }
        }

        for (pm in parsedMessages) {
            if (pm.timestamp > 0L) {
                if (pm.timestamp < dateStart) dateStart = pm.timestamp
                if (pm.timestamp > dateEnd) dateEnd = pm.timestamp
            }

            val canonicalSender = if (pm.isSystem) "System" else senderToCanonical[pm.sender] ?: pm.sender
            val messageType = determineMessageType(pm.text, pm.isSystem)
            val isMedia = messageType != "TEXT" && messageType != "SYSTEM" && messageType != "DELETED"
            val isDeleted = messageType == "DELETED"

            chatMessages.add(
                ChatMessage(
                    sessionId = sessionId,
                    
                    senderName = canonicalSender,
                    timestamp = pm.timestamp,
                    messageText = pm.text,
                    rawMessageText = pm.rawText,
                    messageType = messageType,
                    isMedia = isMedia,
                    isDeleted = isDeleted,
                    isSystem = pm.isSystem
                )
            )

            if (!pm.isSystem) {
                val stats = participantsMap.getOrPut(canonicalSender) {
                    ParticipantStats(name = canonicalSender, firstActive = pm.timestamp)
                }
                stats.messageCount++
                stats.lastActive = pm.timestamp

                if (isMedia) {
                    stats.mediaCount++
                }

                if (!isDeleted && !isMedia) {
                    val text = pm.text
                    stats.characterCount += text.length
                    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    stats.wordCount += words.size

                    // Count emojis
                    val emojiCount = countEmojis(text)
                    stats.emojiCount += emojiCount

                    // Track favorite emoji
                    extractEmojis(text).forEach { emoji ->
                        stats.emojiFreq[emoji] = (stats.emojiFreq[emoji] ?: 0) + 1
                        globalEmojiFreq[emoji] = (globalEmojiFreq[emoji] ?: 0) + 1
                    }

                    // Count links
                    val links = extractLinksList(text)
                    stats.linkCount += links.size
                    globalLinks.addAll(links)
                }
            }
        }

        val participantsList = participantsMap.values.map { stats ->
            // Find favorite emoji
            val favEmoji = stats.emojiFreq.maxByOrNull { it.value }?.key

            val normalizedPhone = canonicalToNormalized[stats.name]
            val isPhone = normalizedPhone != null

            ParsedParticipant(
                
                name = stats.name,
                phone = if (isPhone) stats.name else null,
                normalizedPhone = normalizedPhone,
                messageCount = stats.messageCount,
                wordCount = stats.wordCount,
                characterCount = stats.characterCount,
                mediaCount = stats.mediaCount,
                emojiCount = stats.emojiCount,
                linkCount = stats.linkCount,
                favoriteEmoji = favEmoji,
                firstActive = stats.firstActive,
                lastActive = stats.lastActive
            )
        }

        val allEmojisList = globalEmojiFreq.map { ParsedEmoji(it.key, it.value) }.sortedByDescending { it.count }

        return ParserResult(
            messages = chatMessages,
            participants = participantsList,
            totalMessages = chatMessages.size,
            dateStart = if (dateStart == Long.MAX_VALUE) 0L else dateStart,
            dateEnd = if (dateEnd == Long.MIN_VALUE) 0L else dateEnd,
            allLinks = globalLinks,
            allEmojis = allEmojisList
        )
    }

    private fun parseTimestamp(dateStr: String, timeStr: String): Long {
        val cleanDate = dateStr.replace(".", "/").replace("-", "/").trim()
        // Ensure space before am/pm for consistent parsing
        val cleanTime = timeStr.trim().replace(Regex("(?i)([0-9])([ap]m)"), "$1 $2").replace("\\s+".toRegex(), " ")

        val locales = listOf(Locale.ENGLISH, Locale.getDefault())

        // Try date formats
        for (locale in locales) {
            for (format in dateFormats) {
                try {
                    val sdf = SimpleDateFormat(format, locale)
                    sdf.timeZone = TimeZone.getDefault()
                    sdf.isLenient = false
                    val date = sdf.parse("$cleanDate, $cleanTime")
                    if (date != null && date.time > 0) {
                        return date.time
                    }
                } catch (e: Exception) {
                    // Keep trying
                }
            }
        }

        // If completely unparseable, return 0L instead of current time to avoid future dates 
        return 0L
    }

    private fun determineMessageType(text: String, isSystem: Boolean): String {
        if (isSystem) return "SYSTEM"

        val lowerText = text.lowercase()

        return when {
            lowerText.contains("this message was deleted") || lowerText.contains("you deleted this message") -> "DELETED"
            lowerText.contains("omitted") && (lowerText.contains("media") || lowerText.contains("photo") || lowerText.contains("video") || lowerText.contains("audio") || lowerText.contains("sticker") || lowerText.contains("gif") || lowerText.contains("document") || lowerText.contains("contact")) -> {
                when {
                    lowerText.contains("photo") || lowerText.contains("image") -> "IMAGE"
                    lowerText.contains("video") -> "VIDEO"
                    lowerText.contains("voice note") -> "VOICE_NOTE"
                    lowerText.contains("audio") || lowerText.contains("voice") -> "AUDIO"
                    lowerText.contains("gif") -> "GIF"
                    lowerText.contains("sticker") -> "STICKER"
                    lowerText.contains("document") || lowerText.contains("file") -> "DOCUMENT"
                    lowerText.contains("contact") -> "CONTACT"
                    else -> "IMAGE"
                }
            }
            lowerText.contains("poll:") || lowerText.contains("POLL:") -> "POLL"
            lowerText.contains("<image omitted>") || lowerText.contains("<photo omitted>") || lowerText.contains("photo (file attached)") -> "IMAGE"
            lowerText.contains("<video omitted>") || lowerText.contains("video (file attached)") -> "VIDEO"
            lowerText.contains("voice note (file attached)") -> "VOICE_NOTE"
            lowerText.contains("<audio omitted>") || lowerText.contains("audio (file attached)") || lowerText.contains("voice message") -> "AUDIO"
            lowerText.contains("<sticker omitted>") || lowerText.contains("sticker (file attached)") -> "STICKER"
            lowerText.contains("<gif omitted>") -> "GIF"
            lowerText.contains("document (file attached)") || lowerText.contains("document omitted") -> "DOCUMENT"
            lowerText.contains("contact card omitted") || lowerText.contains("vcard omitted") -> "CONTACT"
            lowerText.contains("location:") || lowerText.contains("maps.google.com") || lowerText.contains("maps.apple.com") -> "LOCATION"
            lowerText.contains("missed video call") -> "MISSED_VIDEO_CALL"
            lowerText.contains("missed voice call") -> "MISSED_VOICE_CALL"
            lowerText.contains("video call") -> "VIDEO_CALL"
            lowerText.contains("voice call") -> "VOICE_CALL"
            extractLinksList(text).isNotEmpty() -> "LINK"
            else -> "TEXT"
        }
    }

    private fun countEmojis(text: String): Int {
        var count = 0
        val len = text.length
        var i = 0
        while (i < len) {
            val codePoint = text.codePointAt(i)
            if (isEmoji(codePoint)) {
                count++
            }
            i += Character.charCount(codePoint)
        }
        return count
    }

    private fun extractEmojis(text: String): List<String> {
        val emojis = mutableListOf<String>()
        val len = text.length
        var i = 0
        while (i < len) {
            val codePoint = text.codePointAt(i)
            if (isEmoji(codePoint)) {
                val emojiStr = String(Character.toChars(codePoint))
                emojis.add(emojiStr)
            }
            i += Character.charCount(codePoint)
        }
        return emojis
    }

    private fun isEmoji(codePoint: Int): Boolean {
        // Quick range checks for common emojis and miscellaneous symbols
        return (codePoint in 0x1F600..0x1F64F) || // Emoticons
                (codePoint in 0x1F300..0x1F5FF) || // Misc Symbols and Pictographs
                (codePoint in 0x1F680..0x1F6FF) || // Transport and Map
                (codePoint in 0x2600..0x26FF) ||     // Misc Symbols
                (codePoint in 0x2700..0x27BF) ||     // Dingbats
                (codePoint in 0x1F900..0x1F9FF) || // Supplemental Symbols and Pictographs
                (codePoint in 0x1FA70..0x1FAFF)    // Symbols and Pictographs Extended-A
    }

    private fun extractLinksList(text: String): List<ParsedLink> {
        val urlPattern = Pattern.compile(
            "\\b(https?|ftp|file)://([-a-zA-Z0-9+&@#/%?=~_|!:,.;]*)[-a-zA-Z0-9+&@#/%=~_|]",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = urlPattern.matcher(text)
        val links = mutableListOf<ParsedLink>()
        while (matcher.find()) {
            val url = matcher.group(0) ?: continue
            val protocol = matcher.group(1) ?: ""
            var domain = matcher.group(2) ?: ""
            // simplified domain extraction, group 2 contains everything after :// 
            // lets find just the domain part:
            val domainEnd = domain.indexOf('/')
            if (domainEnd != -1) {
                domain = domain.substring(0, domainEnd)
            }
            links.add(ParsedLink(url, domain, protocol))
        }
        return links
    }

    data class ParsedLink(
        val url: String,
        val domain: String,
        val protocol: String
    )

    data class ParsedEmoji(
        val emoji: String,
        val count: Int
    )

    private class ParticipantStats(
        val name: String,
        var messageCount: Int = 0,
        var wordCount: Int = 0,
        var characterCount: Int = 0,
        var mediaCount: Int = 0,
        var emojiCount: Int = 0,
        var linkCount: Int = 0,
        val emojiFreq: MutableMap<String, Int> = mutableMapOf(),
        var firstActive: Long = 0L,
        var lastActive: Long = 0L
    )

    
    data class ParsedParticipant(
        val name: String,
        val phone: String?,
        val normalizedPhone: String?,
        val messageCount: Int,
        val wordCount: Int,
        val characterCount: Int,
        val mediaCount: Int,
        val emojiCount: Int,
        val linkCount: Int,
        val favoriteEmoji: String?,
        val firstActive: Long,
        val lastActive: Long
    )
    
    data class ParserResult(

        val messages: List<ChatMessage>,
        val participants: List<ParsedParticipant>,
        val totalMessages: Int,
        val dateStart: Long,
        val dateEnd: Long,
        val allLinks: List<ParsedLink> = emptyList(),
        val allEmojis: List<ParsedEmoji> = emptyList()
    )
    
    private fun normalizeSender(raw: String): String {
        return raw.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeWhitespace(raw: String): String {
        // Replace multiple spaces, tabs, and carriage returns with a single space.
        // We preserve newlines \n for multiline messages, but normal spaces are collapsed.
        return raw.replace(Regex("[\\r\\t]+"), " ").replace(Regex(" {2,}"), " ").trim()
    }
}

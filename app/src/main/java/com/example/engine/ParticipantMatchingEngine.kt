package com.example.engine

import android.content.Context
import com.example.ai.AiService
import com.example.data.Participant
import com.example.parser.ImportedMember

data class MatchingStats(
    val membersImported: Int = 0,
    val participantsFound: Int = 0,
    val mergedParticipants: Int = 0,
    val duplicateNumbersRemoved: Int = 0,
    val duplicateRowsRemoved: Int = 0,
    val headerRowsIgnored: Int = 0,
    val silentMembers: Int = 0,
    val unmatchedMembers: Int = 0,
    val warnings: Int = 0,
    val oldToNewNameMap: Map<String, String> = emptyMap()
)

object ParticipantMatchingEngine {
    
    fun cleanImportedMembers(rawMembers: List<ImportedMember>): Pair<List<ImportedMember>, MatchingStats> {
        var duplicateRowsRemoved = 0
        var headerRowsIgnored = 0
        var duplicateNumbersRemoved = 0
        
        val seenRows = mutableSetOf<String>()
        val canonicalMap = mutableMapOf<String, ImportedMember>()
        val noPhoneList = mutableListOf<ImportedMember>()
        
        val kw = setOf("name", "first", "last", "display_name", "participant", "member", "phone", "mobile", "number", "contact")
        
        for (member in rawMembers) {
            val nameLower = member.originalName.lowercase().trim()
            val phoneLower = member.originalPhone?.lowercase()?.trim() ?: ""
            
            // Header detection
            val isHeader = (nameLower in kw || phoneLower in kw) && 
                           (nameLower.isBlank() || nameLower in kw || nameLower.contains("name") || nameLower.contains("phone")) &&
                           (phoneLower.isBlank() || phoneLower in kw || phoneLower.contains("name") || phoneLower.contains("phone") || phoneLower.contains("number"))
            
            if (isHeader) {
                headerRowsIgnored++
                continue
            }
            
            if (member.originalName.isBlank() && member.originalPhone.isNullOrBlank()) {
                continue // Blank row
            }
            
            val rowKey = "${member.originalName}|${member.originalPhone}"
            if (!seenRows.add(rowKey)) {
                duplicateRowsRemoved++
                continue
            }
            
            if (member.normalizedPhone != null) {
                val existing = canonicalMap[member.normalizedPhone]
                if (existing != null) {
                    duplicateNumbersRemoved++
                    // If the existing one has no name but the new one does, keep the new one's name
                    if ((existing.originalName.isBlank() || existing.originalName.lowercase() == "unknown") && member.originalName.isNotBlank() && member.originalName.lowercase() != "unknown") {
                        canonicalMap[member.normalizedPhone] = ImportedMember(member.originalName, member.originalPhone ?: existing.originalPhone, member.normalizedPhone)
                    }
                } else {
                    canonicalMap[member.normalizedPhone] = member
                }
            } else {
                noPhoneList.add(member)
            }
        }
        
        val cleaned = canonicalMap.values.toList() + noPhoneList
        
        return Pair(cleaned, MatchingStats(
            membersImported = cleaned.size,
            duplicateRowsRemoved = duplicateRowsRemoved,
            headerRowsIgnored = headerRowsIgnored,
            duplicateNumbersRemoved = duplicateNumbersRemoved
        ))
    }

    class MutableParticipant(
        var sessionId: Long = 0,
        var name: String = "",
        var phone: String? = null,
        var normalizedPhone: String? = null,
        var messageCount: Int = 0,
        var wordCount: Int = 0,
        var characterCount: Int = 0,
        var mediaCount: Int = 0,
        var emojiCount: Int = 0,
        var linkCount: Int = 0,
        var favoriteEmoji: String? = null,
        var firstActive: Long = 0L,
        var lastActive: Long = 0L
    ) {
        fun toParticipant(): Participant {
            return Participant(
                sessionId = sessionId,
                name = name,
                phone = phone,
                normalizedPhone = normalizedPhone,
                messageCount = messageCount,
                wordCount = wordCount,
                characterCount = characterCount,
                mediaCount = mediaCount,
                emojiCount = emojiCount,
                linkCount = linkCount,
                favoriteEmoji = favoriteEmoji,
                firstActive = firstActive,
                lastActive = lastActive
            )
        }
    }

    suspend fun matchParticipants(
        sessionId: Long,
        chatParticipants: List<com.example.parser.WhatsAppParser.ParsedParticipant>,
        importedMembers: List<ImportedMember>,
        aiService: AiService?
    ): Pair<List<Participant>, MatchingStats> {
        val (cleanedMembers, initialStats) = cleanImportedMembers(importedMembers)
        
        val canonicalMap = mutableMapOf<String, MutableParticipant>()
        val unmappedParticipants = mutableListOf<MutableParticipant>()
        
        val oldToNewNameMap = mutableMapOf<String, String>()
        var mergedCount = 0
        var silentCount = 0
        var unmatchedCount = 0
        
        // 1. Process Chat Participants
        for (chat in chatParticipants) {
            val norm = chat.normalizedPhone
            if (norm != null && norm.isNotBlank()) {
                val existing = canonicalMap[norm]
                if (existing != null) {
                    // Merge into existing
                    existing.messageCount += chat.messageCount
                    existing.wordCount += chat.wordCount
                    existing.characterCount += chat.characterCount
                    existing.mediaCount += chat.mediaCount
                    existing.emojiCount += chat.emojiCount
                    existing.linkCount += chat.linkCount
                    
                    if (existing.firstActive == 0L || (chat.firstActive > 0 && chat.firstActive < existing.firstActive)) {
                        existing.firstActive = chat.firstActive
                    }
                    if (chat.lastActive > existing.lastActive) {
                        existing.lastActive = chat.lastActive
                    }
                    
                    // Prefer non-phone names for the canonical map
                    val existingClean = existing.name.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0\\u202F]"), "")
                    val chatClean = chat.name.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0\\u202F]"), "")
                    val phoneRegex = Regex("^[+\\d\\s\\-()\\u202F~]+$")
                    
                    val existingIsPhone = existingClean.length >= 7 && phoneRegex.matches(existingClean)
                    val chatIsPhone = chatClean.length >= 7 && phoneRegex.matches(chatClean)
                    
                    if (existingIsPhone && !chatIsPhone) {
                        oldToNewNameMap[existing.name] = chat.name
                        existing.name = chat.name
                    } else if (existing.name != chat.name) {
                        oldToNewNameMap[chat.name] = existing.name
                    }
                } else {
                    val p = MutableParticipant(
                        sessionId = sessionId,
                        name = chat.name,
                        phone = chat.phone,
                        normalizedPhone = norm,
                        messageCount = chat.messageCount,
                        wordCount = chat.wordCount,
                        characterCount = chat.characterCount,
                        mediaCount = chat.mediaCount,
                        emojiCount = chat.emojiCount,
                        linkCount = chat.linkCount,
                        favoriteEmoji = chat.favoriteEmoji,
                        firstActive = chat.firstActive,
                        lastActive = chat.lastActive
                    )
                    canonicalMap[norm] = p
                }
            } else {
                val p = MutableParticipant(
                    sessionId = sessionId,
                    name = chat.name,
                    phone = chat.phone,
                    normalizedPhone = null,
                    messageCount = chat.messageCount,
                    wordCount = chat.wordCount,
                    characterCount = chat.characterCount,
                    mediaCount = chat.mediaCount,
                    emojiCount = chat.emojiCount,
                    linkCount = chat.linkCount,
                    favoriteEmoji = chat.favoriteEmoji,
                    firstActive = chat.firstActive,
                    lastActive = chat.lastActive
                )
                unmappedParticipants.add(p)
            }
        }
        
        // 2. Process Imported Members
        for (member in cleanedMembers) {
            val norm = member.normalizedPhone
            if (norm != null && norm.isNotBlank()) {
                val existing = canonicalMap[norm]
                if (existing != null) {
                    mergedCount++
                    // Merge details, prefer imported name if available and valid
                    val oldName = existing.name
                    if (member.originalName.isNotBlank() && member.originalName.lowercase() != "unknown" && member.originalName != norm) {
                        existing.name = member.originalName
                        if (oldName != existing.name) {
                            oldToNewNameMap[oldName] = existing.name
                        }
                    }
                    if (existing.phone.isNullOrBlank() && !member.originalPhone.isNullOrBlank()) {
                        existing.phone = member.originalPhone
                    }
                } else {
                    unmatchedCount++
                    val p = MutableParticipant(
                        sessionId = sessionId,
                        name = if (member.originalName.isNotBlank()) member.originalName else (member.originalPhone ?: "Unknown"),
                        phone = member.originalPhone,
                        normalizedPhone = norm
                    )
                    canonicalMap[norm] = p
                }
            } else {
                // Try to find by name in unmapped
                var matched = false
                for (u in unmappedParticipants) {
                    if (stringDistance(u.name.lowercase(), member.originalName.lowercase()) <= 2) {
                        mergedCount++
                        if (member.originalName.isNotBlank()) {
                            val oldName = u.name
                            u.name = member.originalName
                            oldToNewNameMap[oldName] = u.name
                        }
                        if (u.phone.isNullOrBlank()) u.phone = member.originalPhone
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    unmatchedCount++
                    unmappedParticipants.add(
                        MutableParticipant(
                            sessionId = sessionId,
                            name = if (member.originalName.isNotBlank()) member.originalName else "Unknown",
                            phone = member.originalPhone,
                            normalizedPhone = null
                        )
                    )
                }
            }
        }
        
        val allParticipants = canonicalMap.values + unmappedParticipants
        for (p in allParticipants) {
            if (p.messageCount <= 10 && (canonicalMap.containsValue(p) || unmappedParticipants.contains(p))) {
                // This is a rough estimation of silent members
            }
        }
        silentCount = allParticipants.count { it.messageCount <= 10 }
        
        // Finalize unique names
        val safeFinalParticipants = mutableListOf<Participant>()
        val usedNames = mutableSetOf<String>()
        
        for (part in allParticipants) {
            var finalName = part.name
            var counter = 1
            while (usedNames.contains(finalName)) {
                finalName = "${part.name} ($counter)"
                counter++
            }
            usedNames.add(finalName)
            
            if (finalName != part.name) {
                val oldNames = oldToNewNameMap.filterValues { it == part.name }.keys.toList()
                for (oldName in oldNames) {
                    oldToNewNameMap[oldName] = finalName
                }
                oldToNewNameMap[part.name] = finalName
                part.name = finalName
            }
            
            safeFinalParticipants.add(part.toParticipant())
        }
        
        val stats = initialStats.copy(
            participantsFound = chatParticipants.size,
            mergedParticipants = mergedCount,
            silentMembers = silentCount,
            unmatchedMembers = unmatchedCount,
            oldToNewNameMap = oldToNewNameMap
        )
        
        android.util.Log.i("ParticipantMatching", "================================================")
        android.util.Log.i("ParticipantMatching", "After import print")
        android.util.Log.i("ParticipantMatching", "Unique Normalized Phones: ${canonicalMap.size}")
        android.util.Log.i("ParticipantMatching", "Merged Participants: ${stats.mergedParticipants}")
        android.util.Log.i("ParticipantMatching", "Participants Before Merge: ${chatParticipants.size + cleanedMembers.size}")
        android.util.Log.i("ParticipantMatching", "Participants After Merge: ${safeFinalParticipants.size}")
        android.util.Log.i("ParticipantMatching", "Duplicates Removed: ${chatParticipants.size + cleanedMembers.size - safeFinalParticipants.size}")
        android.util.Log.i("ParticipantMatching", "================================================")
        
        
        // Final sanity check for duplicates as requested
        val finalNorms = safeFinalParticipants.mapNotNull { it.normalizedPhone }
        val duplicates = finalNorms.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            for (dup in duplicates) {
                println("Class Name\nParticipantMatchingEngine")
                println("Function Name\nmatchParticipants")
                println("Line\n333") // or wherever this sits roughly
                println("Reason\nDuplicate normalized phone ${dup.key} bypassed the merge logic")
            }
        }
        
        return Pair(safeFinalParticipants, stats)

    }
    
    private fun stringDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }
}

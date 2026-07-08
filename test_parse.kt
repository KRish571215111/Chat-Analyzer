import kotlin.text.Regex

fun normalizePhone(phone: String?): String? {
    if (phone == null) return null
    val clean = phone.replace(Regex("[^\\d+]"), "")
    if (clean.isBlank()) return null
    return if (!clean.startsWith("+")) "+$clean" else clean
}

fun main() {
    val senders = listOf("+919876543210", "+91 98765 43210")
    val phoneRegex = Regex("^[+\\d\\s\\-()\\u202F~]+$")
    val senderToCanonical = mutableMapOf<String, String>()
    val canonicalToNormalized = mutableMapOf<String, String>()
    
    val normalizedGroups = mutableMapOf<String, MutableList<String>>()
    
    for (sender in senders) {
        val cleanSenderForCheck = sender.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0\\u202F]"), "")
        val isPhone = cleanSenderForCheck.length >= 7 && phoneRegex.matches(cleanSenderForCheck)
        val normalized = normalizePhone(sender)
        
        if (isPhone && normalized != null && normalized.length >= 7) {
            normalizedGroups.getOrPut(normalized) { mutableListOf() }.add(sender)
        } else {
            senderToCanonical[sender] = sender
            if (normalized != null && normalized.length >= 7) {
                canonicalToNormalized[sender] = normalized
            }
        }
    }
    
    for ((normalized, group) in normalizedGroups) {
        val canonical = group.first()
        for (sender in group) {
            senderToCanonical[sender] = canonical
        }
        canonicalToNormalized[canonical] = normalized
    }
    println(senderToCanonical)
    println(canonicalToNormalized)
}

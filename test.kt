import java.util.regex.Regex

fun main() {
    val phoneRegex = Regex("^[+\\d\\s\\-()]+$")
    
    val sender1 = "+91 98765 43210"
    val cleanSender1 = sender1.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0]"), "")
    println("1: " + phoneRegex.matches(cleanSender1))

    val sender2 = "919876543210"
    val cleanSender2 = sender2.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0]"), "")
    println("2: " + phoneRegex.matches(cleanSender2))
    
    val sender3 = "+1 (234) 567-8901"
    val cleanSender3 = sender3.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0]"), "")
    println("3: " + phoneRegex.matches(cleanSender3))

    val sender4 = "\u202A+91 98765 43210\u202C"
    val cleanSender4 = sender4.replace(Regex("[\\u200E\\u200F\\u202A\\u202B\\u202C\\u202D\\u202E\\u200B\\u00A0]"), "")
    println("4: " + phoneRegex.matches(cleanSender4))
}

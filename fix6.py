import sys
import re

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

start_marker = "                val safeMessages = messagesJson"
end_marker = "                indexFile.writeText(htmlContent)"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker) + len(end_marker)

new_block = """                val safeMessages = messagesJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")
                
                val injectScript = \"\"\"
                <script>
                window.EXPORT_DATA = {
                    config: \$safeConfig,
                    dashboard: \$safeDashboard,
                    members: \$safeMembers,
                    messages: \$safeMessages,
                    media: \$safeMedia,
                    timeline: [],
                    bookmarks: []
                };
                </script>
                \"\"\".trimIndent()
                
                // Insert after opening head tag for maximum safety
                if (htmlContent.contains("<head>")) {
                    htmlContent = htmlContent.replace("<head>", "<head>\\n\$injectScript\\n")
                } else if (htmlContent.contains("</head>")) {
                    htmlContent = htmlContent.replace("</head>", "\$injectScript\\n</head>")
                } else if (htmlContent.contains("<body>")) {
                    htmlContent = htmlContent.replace("<body>", "<body>\\n\$injectScript\\n")
                } else {
                    htmlContent += "\\n\$injectScript"
                }
                indexFile.writeText(htmlContent)"""

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + new_block + content[end_idx:]

    with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
        f.write(content)
else:
    print("Not found")


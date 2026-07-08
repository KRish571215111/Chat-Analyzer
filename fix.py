import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target1 = """                    htmlContent = htmlContent.replace("<head>", "<head>\\nval safeMedia = mediaJson.replace(\\"</script>\\", \\"<\\\\/script>\\").replace(\\"\\\\${\\", \\"\\\\\\\\${\\").replace(\\"{{\\", \\"\\\\{{\\")\\n$injectScript\\n")"""
replacement1 = """                    htmlContent = htmlContent.replace("<head>", "<head>\\n$injectScript\\n")"""

content = content.replace(target1, replacement1)

target2 = """                val safeMessages = messagesJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")"""
replacement2 = """                val safeMessages = messagesJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")"""

content = content.replace(target2, replacement2)

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.write(content)

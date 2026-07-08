import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target1 = """                val safeMessages = messagesJson.replace("</script>", "<\\/script>").replace("\\\${", "\\\${").replace("{{", "\\{{")"""
replacement1 = """                val safeMessages = messagesJson.replace("</script>", "<\\/script>").replace("\\\${", "\\\${").replace("{{", "\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\/script>").replace("\\\${", "\\\${").replace("{{", "\\{{")"""

content = content.replace(target1, replacement1)

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.write(content)

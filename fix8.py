import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target = """                val safeMessages = messagesJson.replace("</script>", "<\\/script>").replace("\\${", "\\\${").replace("{{", "\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\/script>").replace("\\${", "\\\${").replace("{{", "\\{{")"""

replacement = """                val safeMessages = messagesJson.replace("</script>", "<\\/script>").replace("\${", "\\\${").replace("{{", "\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\/script>").replace("\${", "\\\${").replace("{{", "\\{{")"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.write(content)

import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target1 = """                    htmlContent = htmlContent.replace("<head>", "<head>\\nval safeMedia = mediaJson.replace(\\"</script>\\", \\"<\\\\/script>\\").replace(\\"\\\\${\\", \\"\\\\\\\\${\\").replace(\\"{{\\", \\"\\\\{{\\")\\n$injectScript\\n")"""
replacement1 = """                    htmlContent = htmlContent.replace("<head>", "<head>\\n$injectScript\\n")"""
content = content.replace(target1, replacement1)

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.write(content)

import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target = '''                val safeMessages = messagesJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")'''
replacement = '''                val safeMessages = messagesJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\\\/script>").replace("\\\${", "\\\\\\${").replace("{{", "\\\\{{")'''

if 'val safeMessages = messagesJson' in content:
    lines = content.split('\\n')
    for i, line in enumerate(lines):
        if 'val safeMessages = messagesJson' in line:
            indent = line[:len(line) - len(line.lstrip())]
            lines.insert(i+1, indent + 'val safeMedia = mediaJson.replace("</script>", "<\\\\/script>").replace("\\\\${", "\\\\\\\\${").replace("{{", "\\\\{{")')
            break
    content = '\\n'.join(lines)
    
    with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
        f.write(content)
else:
    print('Target not found')

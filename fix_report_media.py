import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target = '''            // Media & others
            val emptyListJson = "[]"
            File(publicDataDir, "media.json").writeText(emptyListJson)'''

replacement = '''            // Media & others
            val mediaListType = com.squareup.moshi.Types.newParameterizedType(List::class.java, MediaFile::class.java)
            val mediaJson = moshi.adapter<List<MediaFile>>(mediaListType).toJson(mediaFiles)
            File(publicDataDir, "media.json").writeText(mediaJson)
            val emptyListJson = "[]"'''

if target in content:
    content = content.replace(target, replacement)
else:
    print('Target 1 not found')

target2 = '''                    media: [],'''
replacement2 = '''                    media: $safeMedia,'''

if target2 in content:
    content = content.replace(target2, replacement2)
    # Also we need to define safeMedia
    target3 = '''                val safeMessages = messagesJson.replace("</script>", "<\\/script>").replace("\${", "\\\${").replace("{{", "\\{{")'''
    replacement3 = '''                val safeMessages = messagesJson.replace("</script>", "<\\/script>").replace("\${", "\\\${").replace("{{", "\\{{")
                val safeMedia = mediaJson.replace("</script>", "<\\/script>").replace("\${", "\\\${").replace("{{", "\\{{")'''
    content = content.replace(target3, replacement3)
else:
    print('Target 2 not found')


with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.write(content)

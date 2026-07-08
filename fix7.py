import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    content = f.read()

target1 = """                    config: \$safeConfig,
                    dashboard: \$safeDashboard,
                    members: \$safeMembers,
                    messages: \$safeMessages,
                    media: \$safeMedia,"""
replacement1 = """                    config: $safeConfig,
                    dashboard: $safeDashboard,
                    members: $safeMembers,
                    messages: $safeMessages,
                    media: $safeMedia,"""

content = content.replace(target1, replacement1)

target2 = """                if (htmlContent.contains("<head>")) {
                    htmlContent = htmlContent.replace("<head>", "<head>\\n\$injectScript\\n")
                } else if (htmlContent.contains("</head>")) {
                    htmlContent = htmlContent.replace("</head>", "\$injectScript\\n</head>")
                } else if (htmlContent.contains("<body>")) {
                    htmlContent = htmlContent.replace("<body>", "<body>\\n\$injectScript\\n")
                } else {
                    htmlContent += "\\n\$injectScript"
                }"""

replacement2 = """                if (htmlContent.contains("<head>")) {
                    htmlContent = htmlContent.replace("<head>", "<head>\\n$injectScript\\n")
                } else if (htmlContent.contains("</head>")) {
                    htmlContent = htmlContent.replace("</head>", "$injectScript\\n</head>")
                } else if (htmlContent.contains("<body>")) {
                    htmlContent = htmlContent.replace("<body>", "<body>\\n$injectScript\\n")
                } else {
                    htmlContent += "\\n$injectScript"
                }"""

content = content.replace(target2, replacement2)

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.write(content)

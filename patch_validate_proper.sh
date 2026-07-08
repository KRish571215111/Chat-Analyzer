#!/bin/bash
# Insert the validation method before the last line (which should be "}")
sed -i '$i \
    // Validation Method added for HTML Export Requirements\
    private fun validateExportedHtml(reportDir: File): List<String> {\
        val report = mutableListOf<String>()\
        report.add("=== EXPORT VALIDATION REPORT ===")\
        report.add("Date: ${Date()}")\
        report.add("")\
\
        // Check required files\
        val requiredDirs = listOf("css", "js", "data", "images", "videos", "audio", "documents", "thumbs", "fonts", "icons", "charts")\
        requiredDirs.forEach { dirName ->\
            val dir = File(reportDir, dirName)\
            if (!dir.exists()) {\
                report.add("[ERROR] Missing directory: $dirName. Fixing...")\
                dir.mkdirs()\
                report.add("[FIXED] Created directory: $dirName")\
            }\
        }\
\
        val htmlFiles = reportDir.listFiles { _, name -> name.endsWith(".html") } ?: emptyArray()\
        \
        // Ensure index exists\
        if (!File(reportDir, "index.html").exists()) {\
            report.add("[ERROR] Missing index.html. Fixing...")\
            File(reportDir, "index.html").writeText(getHtmlTemplate("Dashboard", "Export", "index"))\
            report.add("[FIXED] Created index.html")\
        }\
\
        htmlFiles.forEach { file ->\
            val content = file.readText()\
            var fixedContent = content\
            var modified = false\
\
            report.add("Validating ${file.name}...")\
\
            // Check CSS link\
            if (!content.contains("""href="css/style.css"""")) {\
                report.add("  [ERROR] Missing CSS link in ${file.name}.")\
                if (content.contains("</head>")) {\
                    fixedContent = fixedContent.replace("</head>", """    <link rel="stylesheet" href="css/style.css">\n</head>""")\
                    modified = true\
                    report.add("  [FIXED] Injected CSS link.")\
                }\
            }\
\
            // Check JS script\
            if (!content.contains("""src="js/app.js"""")) {\
                report.add("  [ERROR] Missing JS script in ${file.name}.")\
                if (content.contains("</body>")) {\
                    fixedContent = fixedContent.replace("</body>", """    <script src="js/app.js"></script>\n</body>""")\
                    modified = true\
                    report.add("  [FIXED] Injected JS script.")\
                }\
            }\
\
            // Check Data script\
            if (!content.contains("""src="data/data.js"""")) {\
                report.add("  [ERROR] Missing data.js script in ${file.name}.")\
                if (content.contains("""<script src="js/app.js"></script>""")) {\
                    fixedContent = fixedContent.replace("""<script src="js/app.js"></script>""", """<script src="data/data.js"></script>\n    <script src="js/app.js"></script>""")\
                    modified = true\
                    report.add("  [FIXED] Injected data.js script.")\
                }\
            }\
            \
            // Check broken images\
            val imgRegex = """<img[^>]+src=["'']([^"'']+)["'']""".toRegex()\
            imgRegex.findAll(content).forEach { match ->\
                val src = match.groupValues[1]\
                if (!src.startsWith("http") && !src.startsWith("data:")) {\
                    val imgFile = File(reportDir, src)\
                    if (!imgFile.exists()) {\
                        report.add("  [ERROR] Broken image link: $src")\
                        val placeholder = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiPjxyZWN0IHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiIGZpbGw9IiNjY2MiLz48dGV4dCB4PSI1MCUiIHk9IjUwJSIgZm9udC1zaXplPSIyMHB4IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBkeT0iLjNlbSI+TWlzc2luZyBJbWFnZTwvdGV4dD48L3N2Zz4="\
                        fixedContent = fixedContent.replace(src, placeholder)\
                        modified = true\
                        report.add("  [FIXED] Replaced $src with SVG placeholder.")\
                    }\
                }\
            }\
            \
            // Validate navigation buttons\
            val requiredNavs = listOf("index.html", "messages.html", "members.html")\
            requiredNavs.forEach { nav ->\
                if (!content.contains("""href="$nav"""")) {\
                    report.add("  [WARNING] Navigation to $nav not found in ${file.name}.")\
                }\
            }\
\
            if (modified) {\
                file.writeText(fixedContent)\
            }\
        }\
        \
        // Validate JSON files\
        val jsonFiles = listOf("data/data.json", "data/config.json")\
        jsonFiles.forEach { jsonPath ->\
            val jsonFile = File(reportDir, jsonPath)\
            if (!jsonFile.exists()) {\
                report.add("[ERROR] Missing required JSON file: $jsonPath")\
                jsonFile.parentFile?.mkdirs()\
                jsonFile.writeText("{}")\
                report.add("[FIXED] Created empty $jsonPath")\
            }\
        }\
\
        report.add("=== VALIDATION COMPLETE ===")\
        return report\
    }\
' app/src/main/java/com/example/utils/ReportGenerator.kt

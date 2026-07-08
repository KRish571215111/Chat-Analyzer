#!/bin/bash
# Insert validation step before Zipping
sed -i '/onProgress(95, "Packaging ZIP...")/i \
            onProgress(90, "Validating Export Package...")\
            val validationReport = validateExportedHtml(reportDir)\
            File(reportDir, "ValidationReport.txt").writeText(validationReport.joinToString("\\n"))\
' app/src/main/java/com/example/utils/ReportGenerator.kt

import sys

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "val safeMessages =" in line or "val safeMedia =" in line:
        lines[i] = line.replace('replace("\\\\${"', 'replace("\\${"')

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'w') as f:
    f.writelines(lines)

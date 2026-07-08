with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_line = line.replace("\\{{", "").replace("\\${", "") # These might be throwing off my count, let's just count structurally

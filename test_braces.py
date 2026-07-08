import re

with open('app/src/main/java/com/example/utils/ReportGenerator.kt', 'r') as f:
    text = f.read()

# Remove string literals
text = re.sub(r'".*?"', '""', text, flags=re.DOTALL)
text = re.sub(r'""".*?"""', '""', text, flags=re.DOTALL)

# Remove comments
text = re.sub(r'//.*', '', text)
text = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)

open_count = text.count('{')
close_count = text.count('}')

print("Open:", open_count, "Close:", close_count)

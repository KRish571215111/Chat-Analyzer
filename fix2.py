import sys

with open("app/src/main/java/com/example/ui/screens/ImportWizardScreen.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "@Composable" in line and "fun Step3Summary" in "".join(lines[lines.index(line):lines.index(line)+2]):
        skip = True
    if skip and "fun SummaryRow" in line:
        pass # we are still skipping
    if not skip:
        new_lines.append(line)

# Let's just find the first occurance of @Composable before fun Step3Summary and cut the file there.

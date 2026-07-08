import sys

with open('app/src/main/java/com/example/engine/ParticipantMatchingEngine.kt', 'r') as f:
    content = f.read()

target = '''        // Calculate Silent Members (0 to 10 messages)
        silentCount = finalParticipants.count { it.messageCount in 0..10 }'''

replacement = '''        // Calculate Silent Members (0 to 10 messages)
        // User requested: "Imported members who sent between 0 and 10 messages"
        val importedNormalizedPhones = cleanedMembers.mapNotNull { it.normalizedPhone }.toSet()
        val importedNames = cleanedMembers.map { it.originalName }.toSet()
        
        silentCount = finalParticipants.count { part ->
            part.messageCount in 0..10 && 
            (part.normalizedPhone in importedNormalizedPhones || part.name in importedNames)
        }'''

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found")
    sys.exit(1)

with open('app/src/main/java/com/example/engine/ParticipantMatchingEngine.kt', 'w') as f:
    f.write(content)

sed -i 's/val lastUpdated: Long = System.currentTimeMillis()/val lastUpdated: Long = System.currentTimeMillis(),\n    val isOnboardingComplete: Boolean = false/' app/src/main/java/com/example/data/Entities.kt
sed -i 's/version = 7,/version = 8,/' app/src/main/java/com/example/data/AppDatabase.kt

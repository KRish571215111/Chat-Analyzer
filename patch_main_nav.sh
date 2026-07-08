sed -i 's/bottomBar = {/bottomBar = {\n            if (currentScreen != Screen.Onboarding \&\& currentScreen != Screen.Home \&\& currentScreen != Screen.Workspaces \&\& currentScreen != Screen.TaskCenter) {/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/AppBottomNavigation(/AppBottomNavigation(/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/onNavigate = { viewModel.navigateTo(it) }/onNavigate = { viewModel.navigateTo(it) }/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/            )/            )\n            }/' app/src/main/java/com/example/MainActivity.kt

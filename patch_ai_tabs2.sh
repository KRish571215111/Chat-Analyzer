sed -i 's/1 -> SummariesTab(viewModel)/1 -> SummariesTab(viewModel) { selectedTab = 0 }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/2 -> AnalysisTab(viewModel)/2 -> AnalysisTab(viewModel) { selectedTab = 0 }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/3 -> RecommendationsTab(viewModel)/3 -> RecommendationsTab(viewModel) { selectedTab = 0 }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/4 -> HistoryReportsTab(viewModel)/4 -> HistoryReportsTab(viewModel) { selectedTab = 0 }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt

sed -i 's/fun SummariesTab(viewModel: ChatViewModel)/fun SummariesTab(viewModel: ChatViewModel, onAction: () -> Unit)/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/fun AnalysisTab(viewModel: ChatViewModel)/fun AnalysisTab(viewModel: ChatViewModel, onAction: () -> Unit)/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/fun RecommendationsTab(viewModel: ChatViewModel)/fun RecommendationsTab(viewModel: ChatViewModel, onAction: () -> Unit)/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/fun HistoryReportsTab(viewModel: ChatViewModel)/fun HistoryReportsTab(viewModel: ChatViewModel, onAction: () -> Unit)/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt

sed -i 's/viewModel.askAi("Generate a $title") }/viewModel.askAi("Generate a $title"); onAction() }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/viewModel.askAi(prompt) }/viewModel.askAi(prompt); onAction() }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt
sed -i 's/viewModel.askAi("Tell me how to $title") }/viewModel.askAi("Tell me how to $title"); onAction() }/g' app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt

package ir.smartscanner.docscan.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Crop : Screen("crop")
    object Preview : Screen("preview/{docId}") {
        fun createRoute(docId: String) = "preview/$docId"
    }
}

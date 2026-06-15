package net.secorp.rssreader.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.secorp.rssreader.ui.article.ArticleScreen
import net.secorp.rssreader.ui.feeds.FeedListScreen
import net.secorp.rssreader.ui.items.ItemListScreen

object Routes {
    const val FEEDS = "feeds"
    const val ALL_ITEMS = "items/all"
    const val ITEMS = "items/feed/{feedId}"
    const val ARTICLE = "article/{itemId}"
    fun items(feedId: Long) = "items/feed/$feedId"
    fun article(itemId: Long) = "article/$itemId"
}

@Composable
fun AppNavHost(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.FEEDS) {
        composable(Routes.FEEDS) {
            FeedListScreen(
                onFeedClick = { feedId -> navController.navigate(Routes.items(feedId)) },
                onAllItemsClick = { navController.navigate(Routes.ALL_ITEMS) },
                onSignOut = onSignOut,
            )
        }
        composable(Routes.ALL_ITEMS) {
            // feedId is absent → ItemListViewModel runs in "all" mode.
            ItemListScreen(
                onItemClick = { itemId -> navController.navigate(Routes.article(itemId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ITEMS,
            arguments = listOf(navArgument("feedId") { type = NavType.LongType }),
        ) {
            ItemListScreen(
                onItemClick = { itemId -> navController.navigate(Routes.article(itemId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ARTICLE,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) {
            ArticleScreen(onBack = { navController.popBackStack() })
        }
    }
}

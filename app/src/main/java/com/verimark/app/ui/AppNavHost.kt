package com.verimark.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.VeriMarkDatabase
import com.verimark.app.util.detectMediaType
import com.verimark.app.util.readDisplayName
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNavHost(sharedVideo: StateFlow<Uri?>, onSharedConsumed: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sharedUri by sharedVideo.collectAsState()

    // A video shared into the app opens a new project and navigates straight to it.
    LaunchedEffect(sharedUri) {
        val uri = sharedUri ?: return@LaunchedEffect
        val title = readDisplayName(context, uri) ?: "Shared Video"
        val db = VeriMarkDatabase.get(context)
        val id = db.caseDao().insert(
            CaseEntity(
                title = title,
                date = System.currentTimeMillis(),
                videoUri = uri.toString(),
                mediaType = detectMediaType(context, uri)
            )
        )
        onSharedConsumed()
        navController.navigate("review/$id") { popUpTo("projects") }
    }

    NavHost(navController = navController, startDestination = "projects") {
        composable("projects") {
            ProjectsListScreen(
                onProjectClick = { caseId -> navController.navigate("review/$caseId") }
            )
        }
        composable(
            route = "review/{caseId}",
            arguments = listOf(navArgument("caseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getLong("caseId") ?: return@composable
            VideoReviewScreen(
                caseId = caseId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

package com.keybook.vault.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.LockState
import com.keybook.vault.ui.components.AuroraBackground
import com.keybook.vault.ui.screens.DetailScreen
import com.keybook.vault.ui.screens.EditorScreen
import com.keybook.vault.ui.screens.HomeScreen
import com.keybook.vault.ui.screens.LoadingScreen
import com.keybook.vault.ui.screens.LockScreen
import com.keybook.vault.ui.screens.SetupScreen
import com.keybook.vault.ui.screens.SettingsScreen
import com.keybook.vault.ui.theme.KeybookColors
import kotlinx.coroutines.flow.collectLatest

@Composable
fun KeybookRoot(viewModel: VaultViewModel = viewModel()) {
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    AuroraBackground {
        AnimatedContent(
            targetState = lockState,
            transitionSpec = {
                (fadeIn(tween(360)) + scaleIn(initialScale = 0.985f, animationSpec = tween(360)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "vault-state",
        ) { state ->
            when (state) {
                LockState.LOADING -> LoadingScreen()
                LockState.SETUP -> SetupScreen(viewModel)
                LockState.LOCKED -> LockScreen(
                    viewModel = viewModel,
                    security = security,
                    activity = activity,
                )
                LockState.UNLOCKED -> {
                    val data = vault
                    if (data == null) {
                        LoadingScreen()
                    } else {
                        VaultNavHost(viewModel = viewModel, security = security)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = KeybookColors.SurfaceSoft,
                contentColor = KeybookColors.Text,
                shape = RoundedCornerShape(14.dp),
                actionColor = KeybookColors.Mint,
            )
        }
    }
}

@Composable
private fun VaultNavHost(
    viewModel: VaultViewModel,
    security: com.keybook.vault.data.SecurityState,
) {
    val navController = rememberNavController()
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val data = vault ?: return

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            fadeIn(tween(220)) + scaleIn(initialScale = 0.99f, animationSpec = tween(220))
        },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable("home") {
            HomeScreen(
                vault = data,
                viewModel = viewModel,
                onOpenItem = { id -> navController.navigate("entry/$id") },
                onAddItem = { type -> navController.navigate("editor?type=${type.name}") },
                onSettings = { navController.navigate("settings") },
                onLock = { viewModel.lockNow() },
            )
        }

        composable(
            route = "entry/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val item = data.items.firstOrNull { it.id == id }
            if (item == null) {
                LaunchedEffect(id) { navController.popBackStack() }
                Box(Modifier.fillMaxSize())
            } else {
                DetailScreen(
                    item = item,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = {
                        navController.navigate("editor?type=${item.type.name}&id=${item.id}")
                    },
                )
            }
        }

        composable(
            route = "editor?type={type}&id={id}",
            arguments = listOf(
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = ItemType.PASSWORD.name
                },
                navArgument("id") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val typeName = entry.arguments?.getString("type") ?: ItemType.PASSWORD.name
            val type = runCatching { ItemType.valueOf(typeName) }.getOrDefault(ItemType.PASSWORD)
            val id = entry.arguments?.getString("id").orEmpty()
            val existing = data.items.firstOrNull { it.id == id }
            if (id.isNotBlank() && existing == null) {
                LaunchedEffect(id) { navController.popBackStack() }
                Box(Modifier.fillMaxSize())
            } else {
                EditorScreen(
                    existing = existing,
                    initialType = type,
                    vaultViewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }

        composable("settings") {
            SettingsScreen(
                security = security,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}


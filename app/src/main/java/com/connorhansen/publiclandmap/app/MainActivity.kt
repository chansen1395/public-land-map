package com.connorhansen.publiclandmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.connorhansen.publiclandmap.presentation.navigation.AppNavigation
import com.connorhansen.publiclandmap.presentation.navigation.BottomNavBar
import com.connorhansen.publiclandmap.presentation.navigation.Screen
import com.connorhansen.publiclandmap.presentation.settings.SettingsViewModel
import com.connorhansen.publiclandmap.ui.theme.PublicLandMapTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PublicLandMapTheme {
                var isReady by remember { mutableStateOf(false) }
                var needsOnboarding by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val prefs = dataStore.data.first()
                    needsOnboarding = !(prefs[SettingsViewModel.KEY_ONBOARDING_COMPLETE] ?: false)
                    isReady = true
                }

                if (!isReady) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@PublicLandMapTheme
                }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val scope = rememberCoroutineScope()

                val showBottomBar = currentRoute in listOf(
                    Screen.Map.route,
                    Screen.Marks.route,
                    Screen.Tools.route,
                    Screen.Settings.route
                )

                // Navigate to onboarding on first launch
                LaunchedEffect(needsOnboarding) {
                    if (needsOnboarding) {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Map.route) { inclusive = true }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(Screen.Map.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    AppNavigation(
                        navController = navController,
                        onOnboardingComplete = {
                            scope.launch {
                                dataStore.edit { prefs ->
                                    prefs[SettingsViewModel.KEY_ONBOARDING_COMPLETE] = true
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

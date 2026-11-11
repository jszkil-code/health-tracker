package dev.healthtracker.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.healthtracker.app.data.auth.AuthState
import dev.healthtracker.app.ui.dashboard.DashboardScreen
import dev.healthtracker.app.ui.debuglog.DebugLogScreen
import dev.healthtracker.app.ui.login.LoginScreen
import kotlinx.serialization.Serializable

@Serializable object DashboardRoute
@Serializable object DebugLogRoute

/** Signed-out users only ever see the login screen; the nav graph exists only when signed in. */
@Composable
fun AppRoot(authState: AuthState) {
    when (authState) {
        AuthState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AuthState.SIGNED_OUT -> LoginScreen()
        AuthState.SIGNED_IN -> SignedInNavHost()
    }
}

@Composable
private fun SignedInNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = DashboardRoute) {
        composable<DashboardRoute> { DashboardScreen(onOpenDebugLog = { nav.navigate(DebugLogRoute) }) }
        composable<DebugLogRoute> { DebugLogScreen(onBack = { nav.popBackStack() }) }
    }
}

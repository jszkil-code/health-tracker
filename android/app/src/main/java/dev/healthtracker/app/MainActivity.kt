package dev.healthtracker.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.healthtracker.app.data.auth.AuthRepository
import dev.healthtracker.app.data.auth.AuthState
import dev.healthtracker.app.ui.lock.AppLock
import dev.healthtracker.app.ui.lock.LockedScreen
import dev.healthtracker.app.ui.navigation.AppRoot
import dev.healthtracker.app.ui.theme.HealthTrackerTheme
import javax.inject.Inject

/**
 * Single activity. FragmentActivity (not ComponentActivity) because BiometricPrompt needs it.
 * The app re-locks every time it leaves the foreground.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    private var locked by mutableStateOf(true)
    private var lockMessage by mutableStateOf<String?>(null)
    private var authInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HealthTrackerTheme {
                Surface {
                    val authState by authRepository.authState.collectAsStateWithLifecycle(AuthState.LOADING)
                    if (locked) {
                        LockedScreen(AppLock.canAuthenticate(this), lockMessage, ::unlock)
                    } else {
                        AppRoot(authState)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (locked && !authInProgress) unlock()
    }

    override fun onStop() {
        super.onStop()
        // The device-credential screen can stop this activity; don't re-lock mid-prompt.
        if (!authInProgress && !isChangingConfigurations) locked = true
    }

    private fun unlock() {
        if (!AppLock.canAuthenticate(this)) return
        authInProgress = true
        AppLock.prompt(
            this,
            onSuccess = {
                authInProgress = false
                locked = false
                lockMessage = null
            },
            onFailure = { msg ->
                authInProgress = false
                lockMessage = msg
            },
        )
    }
}

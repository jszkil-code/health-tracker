package dev.healthtracker.app.data.auth

import dev.healthtracker.app.data.log.AppLogger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue

enum class AuthState { LOADING, SIGNED_OUT, SIGNED_IN }

/** Email + password login against the single account in the private Supabase project. */
@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val logger: AppLogger,
) {
    val authState: Flow<AuthState> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SIGNED_IN
            is SessionStatus.Initializing -> AuthState.LOADING
            else -> AuthState.SIGNED_OUT
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        val (result, elapsed) = measureTimedValue {
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
        }
        result.onSuccess { logger.info("auth", "Sign-in succeeded", elapsed.inWholeMilliseconds) }
            .onFailure { logger.error("auth", "Sign-in failed", it) }
        return result
    }

    suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
            .onSuccess { logger.info("auth", "Signed out") }
            .onFailure { logger.error("auth", "Sign-out failed", it) }
    }
}

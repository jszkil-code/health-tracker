package dev.healthtracker.app.data.auth

import dev.healthtracker.app.BuildConfig

/** Supabase connection values injected at build time (CI secrets or local.properties). */
object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL
    val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    val isConfigured: Boolean get() = url.startsWith("https://") && publishableKey.isNotBlank()

    /** Placeholder so the client can be constructed in unconfigured builds; never contacted. */
    const val PLACEHOLDER_URL = "https://not-configured.invalid"
}

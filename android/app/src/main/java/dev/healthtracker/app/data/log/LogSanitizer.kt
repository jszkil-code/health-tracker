package dev.healthtracker.app.data.log

/**
 * Last line of defense for the debug log: strips anything that looks like a credential or
 * personal identifier. Callers must still log only counts, timings and error types, never
 * raw health values (docs/SPEC.md, "Security and privacy").
 */
object LogSanitizer {
    private const val MAX_LENGTH = 500

    private val patterns = listOf(
        // JSON Web Tokens (Supabase access/refresh tokens)
        Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""") to "[jwt]",
        // Authorization headers
        Regex("""(?i)bearer\s+[A-Za-z0-9._~+/=-]+""") to "Bearer [redacted]",
        // Supabase, Anthropic and generic secret-looking keys
        Regex("""\b(sb_(publishable|secret)_|sk-ant-)[A-Za-z0-9_-]+""") to "[key]",
        // Query-string secrets
        Regex("""(?i)\b(apikey|api_key|access_token|refresh_token|token|password|code)=[^&\s]+""") to "$1=[redacted]",
        // Email addresses
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to "[email]",
    )

    fun sanitize(message: String): String {
        var out = message
        for ((regex, replacement) in patterns) out = regex.replace(out, replacement)
        return if (out.length > MAX_LENGTH) out.take(MAX_LENGTH) + "…" else out
    }
}

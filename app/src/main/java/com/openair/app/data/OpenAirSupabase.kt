package com.openair.app.data

import android.util.Log
import com.openair.app.config.OpenAirConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy Supabase client. Uses the publishable key from openair.local.properties;
 * the service-role key is never present in the app. Sessions (anonymous for
 * now) persist across launches and auto-refresh.
 */
object OpenAirSupabase {
    private const val TAG = "OpenAirSupabase"

    val isConfigured: Boolean
        get() = OpenAirConfig.hasSupabaseClientConfig

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = normalizeUrl(OpenAirConfig.supabaseUrl),
            supabaseKey = OpenAirConfig.supabaseAnonKey.trim()
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    // The dashboard's Data API page shows the REST endpoint (…/rest/v1), but
    // supabase-kt wants the bare project URL — accept either.
    private fun normalizeUrl(raw: String): String =
        raw.trim().trimEnd('/').removeSuffix("/rest/v1").trimEnd('/')

    /**
     * Returns the signed-in user id, signing in anonymously on first run.
     * Null when unconfigured or offline — callers fall back to seed content.
     */
    suspend fun ensureSignedIn(): String? {
        if (!isConfigured) return null
        // IO dispatcher: first access constructs the client (TLS setup, session
        // storage reads) — keep that and everything after off the main thread.
        return withContext(Dispatchers.IO) {
            try {
                val auth = client.auth
                auth.awaitInitialization()
                if (auth.currentSessionOrNull() == null) {
                    auth.signInAnonymously()
                }
                auth.currentUserOrNull()?.id
            } catch (t: Throwable) {
                Log.w(TAG, "Sign-in failed", t)
                null
            }
        }
    }
}

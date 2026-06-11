package com.openair.app.config

import com.openair.app.BuildConfig

object OpenAirConfig {
    val apiBaseUrl: String = BuildConfig.OPENAIR_API_BASE_URL
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    val hasSupabaseClientConfig: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

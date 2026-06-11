package com.openair.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.openair.app.ui.OpenAirApp
import com.openair.app.ui.OpenAirTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenAirTheme {
                OpenAirApp()
            }
        }
    }
}

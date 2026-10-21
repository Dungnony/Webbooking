package com.nhom2.booking

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nhom2.booking.data.settings.LanguageSettings
import com.nhom2.booking.ui.BookingApp
import com.nhom2.booking.ui.theme.BookingTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every top bar is dark blue, so the status bar icons stay light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val container = (application as BookingApplication).container
        setContent {
            BookingTheme {
                BookingApp(container)
            }
        }
    }
}

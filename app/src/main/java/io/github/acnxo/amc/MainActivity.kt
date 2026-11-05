package io.github.acnxo.amc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.acnxo.amc.ui.AppRoot
import io.github.acnxo.amc.ui.theme.AMCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AMCTheme {
                AppRoot()
            }
        }
    }
}

package com.david.carpcast

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.david.carpcast.ui.home.CarpCastTopBar
import com.david.carpcast.ui.home.HomeScreen
import com.david.carpcast.ui.theme.CarpCastTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarpCastTheme {
                Surface {
                    CarpCastApp()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarpCastApp() {
    Scaffold(
        topBar = { CarpCastTopBar() }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            HomeScreen()
        }
    }
}

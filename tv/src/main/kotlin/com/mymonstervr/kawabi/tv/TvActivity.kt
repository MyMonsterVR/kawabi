package com.mymonstervr.kawabi.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mymonstervr.kawabi.tv.nav.TvNavHost
import com.mymonstervr.kawabi.tv.theme.KawabiTvTheme

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KawabiTvTheme {
                TvNavHost()
            }
        }
    }
}

package com.swp81x.nrsuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.swp81x.nrsuite.ui.NRSuiteApp
import com.swp81x.nrsuite.ui.theme.NRSuiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NRSuiteTheme {
                NRSuiteApp()
            }
        }
    }
}

package com.hiro.mistakebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiro.mistakebook.ui.MistakeBookApp
import com.hiro.mistakebook.ui.theme.MistakeBookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MistakeBookTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(application)
                )
                MistakeBookApp(viewModel)
            }
        }
    }
}

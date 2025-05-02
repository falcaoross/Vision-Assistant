package com.programminghut.realtime_object

import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.programminghut.realtime_object.ui.camera.MainScreen
import com.programminghut.realtime_object.ui.theme.JetpackComposeMLKitTutorialTheme
import com.programminghut.realtime_object.ui.MainScreen
import com.programminghut.realtime_object.ui.theme.JetpackComposeMLKitTutorialTheme
import java.lang.reflect.Modifier

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JetpackComposeMLKitTutorialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

package com.kursi.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kursi.core.feedback.FeedbackAndroid
import com.kursi.shared.KursiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // M3: hand the feedback layer an application Context so moment haptics can route
        // through the real system Vibrator. SFX work without this; haptics no-op until set.
        FeedbackAndroid.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            KursiApp()
        }
    }
}

package com.kursi.android

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.kursi.core.feedback.FeedbackAndroid
import com.kursi.core.feedback.NotificationChannelManager
import com.kursi.core.feedback.NotificationPermission
import com.kursi.core.feedback.NotificationPermissionState
import com.kursi.core.prefs.AppPrefs
import com.kursi.shared.KursiApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appPrefs = AppPrefs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FeedbackAndroid.install(applicationContext)
        NotificationChannelManager.createChannels(this)
        updateNotificationPermissionState()
        scheduleInAppReview()
        checkForUpdate()
        enableEdgeToEdge()
        setContent {
            KursiApp()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionState()
    }

    private fun updateNotificationPermissionState() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) NotificationPermission.GRANTED else NotificationPermission.NOT_ASKED
        } else {
            NotificationPermission.GRANTED
        }
        NotificationPermissionState.update(permission)
    }

    private fun scheduleInAppReview() {
        lifecycleScope.launch {
            appPrefs.ledgerFlow.collect { ledger ->
                if (ledger.wins >= 3 && appPrefs.shouldShowReview(BuildConfig.VERSION_NAME)) {
                    launchInAppReview()
                }
            }
        }
    }

    private fun launchInAppReview() {
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(this, task.result).addOnCompleteListener {
                    appPrefs.markReviewShown(BuildConfig.VERSION_NAME)
                }
            }
        }
    }

    private fun checkForUpdate() {
        val manager = AppUpdateManagerFactory.create(this)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val staleDays = info.clientVersionStalenessDays() ?: 0
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && staleDays >= 30
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                manager.startUpdateFlow(
                    info,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }
        }
    }
}

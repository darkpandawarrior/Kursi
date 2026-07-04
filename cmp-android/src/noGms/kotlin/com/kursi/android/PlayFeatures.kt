package com.kursi.android

import androidx.activity.ComponentActivity
import com.kursi.core.prefs.AppPrefs

/** F-Droid (noGms) flavor: no Play Core dependency, so review/update prompts are no-ops. */
object PlayFeatures {
    fun launchInAppReview(activity: ComponentActivity, appPrefs: AppPrefs) = Unit
    fun checkForUpdate(activity: ComponentActivity) = Unit
}

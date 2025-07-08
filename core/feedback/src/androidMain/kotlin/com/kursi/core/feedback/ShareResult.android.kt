package com.kursi.core.feedback

import android.content.Intent

actual fun shareGameResult(text: String) {
    val ctx = FeedbackAndroid.appContext ?: return
    val chooser = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        null,
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(chooser)
}

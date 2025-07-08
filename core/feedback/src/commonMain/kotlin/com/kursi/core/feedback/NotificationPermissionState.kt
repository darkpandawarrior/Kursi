package com.kursi.core.feedback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationPermission { GRANTED, DENIED, NOT_ASKED }

object NotificationPermissionState {
    private val _permission = MutableStateFlow(NotificationPermission.NOT_ASKED)
    val flow: StateFlow<NotificationPermission> = _permission.asStateFlow()

    fun update(permission: NotificationPermission) {
        _permission.value = permission
    }
}

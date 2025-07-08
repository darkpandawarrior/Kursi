package com.kursi.core.feedback

enum class HapticStyle { Light, Medium, Heavy }

enum class HapticNotificationType { Success, Warning, Error }

object HapticManager {
    private val player: SoundPlayer by lazy { defaultSoundPlayer() }

    fun hapticImpact(style: HapticStyle) {
        player.haptic(
            when (style) {
                HapticStyle.Light -> HapticPattern.Tick
                HapticStyle.Medium -> HapticPattern.Thud
                HapticStyle.Heavy -> HapticPattern.HeavyLong
            },
        )
    }

    fun hapticNotification(type: HapticNotificationType) {
        player.haptic(
            when (type) {
                HapticNotificationType.Success -> HapticPattern.HeavyLong
                HapticNotificationType.Warning -> HapticPattern.DoubleBuzz
                HapticNotificationType.Error -> HapticPattern.DoubleBuzz
            },
        )
    }
}

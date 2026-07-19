package com.kursi.android

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KursiNotificationChannels.specs] ids are referenced by the FCM service and must stay stable —
 * this guards against an accidental rename or duplicate id breaking that wiring silently.
 */
class KursiNotificationChannelsTest {
    @Test
    fun `spec ids match the public constants`() {
        val ids = KursiNotificationChannels.specs.map { it.id }
        assertEquals(
            listOf(KursiNotificationChannels.GAME_INVITES, KursiNotificationChannels.SYSTEM),
            ids,
        )
    }

    @Test
    fun `spec ids are unique`() {
        val ids = KursiNotificationChannels.specs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every spec has a non-blank name and description`() {
        KursiNotificationChannels.specs.forEach { spec ->
            assertTrue("id=${spec.id} has a blank name", spec.name.isNotBlank())
            assertTrue("id=${spec.id} has a blank description", spec.description.isNotBlank())
        }
    }

    @Test
    fun `game invites channel is high importance so match alerts are not silenced`() {
        val gameInvites = KursiNotificationChannels.specs.first { it.id == KursiNotificationChannels.GAME_INVITES }
        assertEquals(NotificationManager.IMPORTANCE_HIGH, gameInvites.importance)
    }
}

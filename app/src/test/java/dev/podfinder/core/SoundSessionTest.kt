package dev.podfinder.core

import dev.podfinder.core.SoundSession.Action
import dev.podfinder.core.SoundSession.Event
import dev.podfinder.core.SoundSession.Result
import dev.podfinder.core.SoundSession.State
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SoundSessionTest {

    private fun services(vararg protocols: SoundProtocol): Map<UUID, Set<UUID>> =
        protocols.associate { it.serviceUuid to setOf(it.characteristicUuid) }

    private fun run(events: List<Event>): Pair<State, List<Action>> {
        var state: State = State.Idle
        val actions = mutableListOf<Action>()
        for (event in events) {
            val transition = SoundSession.reduce(state, event)
            state = transition.state
            actions += transition.actions
        }
        return state to actions
    }

    @Test
    fun `opcodes match AirGuard and DULT draft`() {
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x03), SoundProtocol.FIND_MY.startCommand)
        assertArrayEquals(byteArrayOf(0x01, 0x01, 0x03), SoundProtocol.FIND_MY.stopCommand)
        assertArrayEquals(byteArrayOf(0x00, 0x03), SoundProtocol.DULT.startCommand)
        assertArrayEquals(byteArrayOf(0x01, 0x03), SoundProtocol.DULT.stopCommand)
        assertEquals(UUID.fromString("0000fd44-0000-1000-8000-00805f9b34fb"), SoundProtocol.FIND_MY.serviceUuid)
    }

    @Test
    fun `prefers Find My service over DULT when both exist`() {
        assertEquals(SoundProtocol.FIND_MY, SoundProtocol.select(services(SoundProtocol.DULT, SoundProtocol.FIND_MY)))
        assertEquals(SoundProtocol.DULT, SoundProtocol.select(services(SoundProtocol.DULT)))
    }

    @Test
    fun `requires the characteristic, not just the service`() {
        val discovered = mapOf(SoundProtocol.FIND_MY.serviceUuid to emptySet<UUID>())

        assertEquals(null, SoundProtocol.select(discovered))
    }

    @Test
    fun `full Find My session plays then stops`() {
        val (state, actions) = run(
            listOf(
                Event.Start,
                Event.Connected,
                Event.ServicesDiscovered(true, services(SoundProtocol.FIND_MY)),
                Event.DescriptorWritten(true),
                Event.CharacteristicWritten(true),
                Event.PlayTimerElapsed,
                Event.CharacteristicWritten(true),
            ),
        )

        val played = Result.Played(SoundProtocol.FIND_MY)
        assertEquals(State.Finished(played), state)
        assertEquals(
            listOf(
                Action.Connect,
                Action.DiscoverServices,
                Action.EnableNotifications(SoundProtocol.FIND_MY),
                Action.Write(SoundProtocol.FIND_MY, byteArrayOf(0x01, 0x00, 0x03)),
                Action.SchedulePlayTimer(SoundSession.PLAY_MILLIS),
                Action.Write(SoundProtocol.FIND_MY, byteArrayOf(0x01, 0x01, 0x03)),
                Action.Disconnect,
                Action.Report(played),
            ),
            actions,
        )
    }

    @Test
    fun `missing sound service fails with a separated-state hint`() {
        val (state, actions) = run(
            listOf(Event.Start, Event.Connected, Event.ServicesDiscovered(true, emptyMap())),
        )

        val result = (state as State.Finished).result as Result.Failed
        assertTrue(result.reason.contains("separated"))
        assertEquals(Action.Disconnect, actions[actions.size - 2])
    }

    @Test
    fun `rejected start write fails`() {
        val (state, _) = run(
            listOf(
                Event.Start,
                Event.Connected,
                Event.ServicesDiscovered(true, services(SoundProtocol.DULT)),
                Event.DescriptorWritten(true),
                Event.CharacteristicWritten(false),
            ),
        )

        assertTrue((state as State.Finished).result is Result.Failed)
    }

    @Test
    fun `AirTag protocol writes trigger and completes on remote disconnect`() {
        val (state, actions) = run(
            listOf(
                Event.Start,
                Event.Connected,
                Event.ServicesDiscovered(true, services(SoundProtocol.AIRTAG)),
                Event.CharacteristicWritten(true),
                Event.Disconnected(SoundSession.GATT_STATUS_REMOTE_TERMINATED),
            ),
        )

        assertEquals(State.Finished(Result.Played(SoundProtocol.AIRTAG)), state)
        assertTrue(actions.contains(Action.Write(SoundProtocol.AIRTAG, byteArrayOf(0xAF.toByte()))))
    }

    @Test
    fun `disconnect during connect fails without a second disconnect`() {
        val (state, actions) = run(listOf(Event.Start, Event.Disconnected(133)))

        assertTrue((state as State.Finished).result is Result.Failed)
        assertEquals(listOf(Action.Connect, Action.Report((state.result))), actions)
    }

    @Test
    fun `finished session ignores late events`() {
        val (state, actions) = run(listOf(Event.Start, Event.Timeout, Event.Connected, Event.Timeout))

        assertTrue((state as State.Finished).result is Result.Failed)
        assertEquals(3, actions.size)
    }
}

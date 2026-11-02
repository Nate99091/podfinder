package dev.podfinder.core

import java.util.UUID

/**
 * Pure state machine for one "play sound" GATT session. The Android layer feeds GATT callbacks in
 * as [Event]s and executes the returned [Action]s, which keeps the protocol flow unit-testable.
 *
 * Flow (mirrors AirGuard's AppleFindMy session): connect -> discover services -> pick protocol ->
 * enable notifications (if used) -> write start -> play for [PLAY_MILLIS] -> write stop -> disconnect.
 */
object SoundSession {
    const val PLAY_MILLIS = 5_000L
    const val GATT_STATUS_REMOTE_TERMINATED = 19

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Discovering : State
        data class EnablingNotifications(val protocol: SoundProtocol) : State
        data class Starting(val protocol: SoundProtocol) : State
        data class Playing(val protocol: SoundProtocol) : State
        data class Stopping(val protocol: SoundProtocol) : State
        data class Finished(val result: Result) : State
    }

    sealed interface Result {
        data class Played(val protocol: SoundProtocol) : Result
        data class Failed(val reason: String) : Result
    }

    sealed interface Event {
        data object Start : Event
        data object Connected : Event
        data class Disconnected(val status: Int) : Event
        data class ServicesDiscovered(val isSuccess: Boolean, val services: Map<UUID, Set<UUID>>) : Event
        data class DescriptorWritten(val isSuccess: Boolean) : Event
        data class CharacteristicWritten(val isSuccess: Boolean) : Event
        data object PlayTimerElapsed : Event
        data object Timeout : Event
    }

    sealed interface Action {
        data object Connect : Action
        data object DiscoverServices : Action
        data class EnableNotifications(val protocol: SoundProtocol) : Action
        data class Write(val protocol: SoundProtocol, val value: ByteArray) : Action {
            override fun equals(other: Any?): Boolean =
                other is Write && other.protocol == protocol && other.value.contentEquals(value)

            override fun hashCode(): Int = 31 * protocol.hashCode() + value.contentHashCode()
        }
        data class SchedulePlayTimer(val delayMillis: Long) : Action
        data object Disconnect : Action
        data class Report(val result: Result) : Action
    }

    data class Transition(val state: State, val actions: List<Action>)

    fun reduce(state: State, event: Event): Transition {
        if (state is State.Finished) return Transition(state, emptyList())
        return when (event) {
            Event.Start -> onStart(state)
            Event.Connected -> onConnected(state)
            is Event.Disconnected -> onDisconnected(state, event.status)
            is Event.ServicesDiscovered -> onServicesDiscovered(state, event)
            is Event.DescriptorWritten -> onDescriptorWritten(state, event.isSuccess)
            is Event.CharacteristicWritten -> onCharacteristicWritten(state, event.isSuccess)
            Event.PlayTimerElapsed -> onPlayTimerElapsed(state)
            Event.Timeout -> fail("Timed out (${describe(state)})", disconnect = true)
        }
    }

    private fun onStart(state: State): Transition =
        if (state is State.Idle) Transition(State.Connecting, listOf(Action.Connect)) else ignore(state)

    private fun onConnected(state: State): Transition =
        if (state is State.Connecting) {
            Transition(State.Discovering, listOf(Action.DiscoverServices))
        } else {
            ignore(state)
        }

    private fun onDisconnected(state: State, status: Int): Transition = when (state) {
        is State.Stopping -> succeed(state.protocol, disconnect = false)
        is State.Playing ->
            if (state.protocol == SoundProtocol.AIRTAG || status == GATT_STATUS_REMOTE_TERMINATED) {
                succeed(state.protocol, disconnect = false)
            } else {
                fail("Connection lost while playing (status $status)", disconnect = false)
            }
        else -> fail("Disconnected (status $status) while ${describe(state)}", disconnect = false)
    }

    private fun onServicesDiscovered(state: State, event: Event.ServicesDiscovered): Transition {
        if (state !is State.Discovering) return ignore(state)
        if (!event.isSuccess) return fail("Service discovery failed", disconnect = true)
        val protocol = SoundProtocol.select(event.services)
            ?: return fail(
                "No play-sound service found. The AirPods may not be separated from their owner, " +
                    "or are not registered to Find My.",
                disconnect = true,
            )
        return if (protocol.usesNotifications) {
            Transition(State.EnablingNotifications(protocol), listOf(Action.EnableNotifications(protocol)))
        } else {
            Transition(
                State.Playing(protocol),
                listOf(Action.Write(protocol, protocol.startCommand), Action.SchedulePlayTimer(PLAY_MILLIS)),
            )
        }
    }

    private fun onDescriptorWritten(state: State, isSuccess: Boolean): Transition {
        if (state !is State.EnablingNotifications) return ignore(state)
        if (!isSuccess) return fail("Could not enable notifications", disconnect = true)
        return Transition(
            State.Starting(state.protocol),
            listOf(Action.Write(state.protocol, state.protocol.startCommand)),
        )
    }

    private fun onCharacteristicWritten(state: State, isSuccess: Boolean): Transition = when (state) {
        is State.Starting ->
            if (isSuccess) {
                Transition(State.Playing(state.protocol), listOf(Action.SchedulePlayTimer(PLAY_MILLIS)))
            } else {
                fail("AirPods rejected the play-sound command (likely not in separated state)", disconnect = true)
            }
        is State.Playing -> ignore(state)
        is State.Stopping -> succeed(state.protocol, disconnect = true)
        else -> ignore(state)
    }

    private fun onPlayTimerElapsed(state: State): Transition {
        if (state !is State.Playing) return ignore(state)
        val stop = state.protocol.stopCommand ?: return succeed(state.protocol, disconnect = true)
        return Transition(State.Stopping(state.protocol), listOf(Action.Write(state.protocol, stop)))
    }

    private fun succeed(protocol: SoundProtocol, disconnect: Boolean): Transition {
        val result = Result.Played(protocol)
        val actions = listOfNotNull(Action.Disconnect.takeIf { disconnect }, Action.Report(result))
        return Transition(State.Finished(result), actions)
    }

    private fun fail(reason: String, disconnect: Boolean): Transition {
        val result = Result.Failed(reason)
        val actions = listOfNotNull(Action.Disconnect.takeIf { disconnect }, Action.Report(result))
        return Transition(State.Finished(result), actions)
    }

    private fun ignore(state: State) = Transition(state, emptyList())

    private fun describe(state: State): String = when (state) {
        State.Idle -> "idle"
        State.Connecting -> "connecting"
        State.Discovering -> "discovering services"
        is State.EnablingNotifications -> "enabling notifications"
        is State.Starting -> "starting sound"
        is State.Playing -> "playing"
        is State.Stopping -> "stopping sound"
        is State.Finished -> "finished"
    }
}

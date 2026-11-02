package dev.podfinder

import android.content.Context
import dev.podfinder.core.PinnedSelection

/** Persists the pinned device and its last-seen status so pins survive restarts and feed the widget. */
class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadPin(): PinnedSelection.Pin? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        return PinnedSelection.Pin(address, title)
    }

    fun savePin(pin: PinnedSelection.Pin) {
        prefs.edit().putString(KEY_ADDRESS, pin.address).putString(KEY_TITLE, pin.title).apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_TITLE).apply()
    }

    fun saveLastStatus(title: String, detail: String) {
        prefs.edit()
            .putString(KEY_LAST_TITLE, title)
            .putString(KEY_LAST_DETAIL, detail)
            .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
            .apply()
    }

    val lastTitle: String? get() = prefs.getString(KEY_LAST_TITLE, null) ?: prefs.getString(KEY_TITLE, null)
    val lastDetail: String? get() = prefs.getString(KEY_LAST_DETAIL, null)

    companion object {
        private const val NAME = "podfinder"
        private const val KEY_ADDRESS = "pin.address"
        private const val KEY_TITLE = "pin.title"
        private const val KEY_LAST_TITLE = "last.title"
        private const val KEY_LAST_DETAIL = "last.detail"
        private const val KEY_LAST_SEEN = "last.seen"
    }
}

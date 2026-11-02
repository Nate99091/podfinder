package dev.podfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.podfinder.ble.BleScanner
import dev.podfinder.ble.GattSoundPlayer
import dev.podfinder.ble.Haptics
import dev.podfinder.core.HapticCadence
import dev.podfinder.core.NearbyDevices
import dev.podfinder.core.OfflineFinding
import dev.podfinder.core.PinnedSelection
import dev.podfinder.core.ProximityMeter
import dev.podfinder.core.ProximityPairing
import dev.podfinder.core.SoundSession
import dev.podfinder.ui.SignalMeterView

/**
 * Two modes in one screen: a list of nearby AirPods / Find My devices, and a full-screen "walk
 * toward them" finder once one is pinned. The finder drives a radial meter plus haptics.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var nearby = NearbyDevices()
    private var scanner: BleScanner? = null
    private var activeSound: GattSoundPlayer? = null
    private var soundTargetAddress: String? = null
    private var haptics: Haptics? = null

    private var pin: PinnedSelection.Pin? = null

    private lateinit var prefs: PinStore
    private lateinit var listRoot: View
    private lateinit var finderRoot: View
    private lateinit var statusView: TextView
    private lateinit var deviceListView: LinearLayout
    private lateinit var permissionButton: Button

    // Finder views
    private lateinit var meter: SignalMeterView
    private lateinit var finderTitle: TextView
    private lateinit var warmthView: TextView
    private lateinit var distanceView: TextView
    private lateinit var detailView: TextView
    private lateinit var finderSoundButton: Button

    private val refresh = object : Runnable {
        override fun run() {
            nearby = nearby.expire(SystemClock.elapsedRealtime())
            render()
            handler.postDelayed(this, REFRESH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PinStore(this)
        pin = prefs.loadPin()
        haptics = Haptics(this)
        setContentView(buildLayout())
        if (intent?.getBooleanExtra(EXTRA_FIND_PINNED, false) == true && pin == null) {
            // Launched from the widget with nothing pinned yet; just show the list.
        }
    }

    override fun onResume() {
        super.onResume()
        startIfPossible()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
        haptics?.stop()
        stopScan()
        PodFinderWidget.refreshAll(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) startIfPossible()
    }

    private fun hasPermissions(): Boolean =
        REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun adapter(): BluetoothAdapter? = getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission") // guarded by hasPermissions()
    private fun startIfPossible() {
        if (!hasPermissions()) {
            permissionButton.visibility = View.VISIBLE
            statusView.setText(R.string.permission_needed)
            return
        }
        permissionButton.visibility = View.GONE
        val adapter = adapter()
        if (adapter == null || !adapter.isEnabled) {
            statusView.setText(R.string.bluetooth_off)
            return
        }
        val current = scanner ?: BleScanner(
            adapter = adapter,
            onAdvert = { address, data, rssi ->
                handler.post { nearby = nearby.onAdvertisement(address, data, rssi, SystemClock.elapsedRealtime()) }
            },
            onError = { message -> handler.post { statusView.text = message } },
        ).also { scanner = it }
        current.start()
        statusView.setText(R.string.scanning)
    }

    @SuppressLint("MissingPermission") // scanner only exists once permissions were granted
    private fun stopScan() {
        if (hasPermissions()) scanner?.stop()
    }

    // ---- pinning ----

    private fun pinDevice(device: NearbyDevices.Device) {
        pin = PinnedSelection.pinFor(device).also { prefs.savePin(it) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        haptics?.start()
        render()
    }

    private fun unpin() {
        pin = null
        prefs.clearPin()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        haptics?.stop()
        render()
    }

    // ---- sound ----

    private fun playSound(address: String) {
        val adapter = adapter() ?: return
        if (!hasPermissions() || activeSound != null) return
        soundTargetAddress = address
        activeSound = GattSoundPlayer(this, adapter, address) { result ->
            activeSound = null
            soundTargetAddress = null
            val message = when (result) {
                is SoundSession.Result.Played -> getString(R.string.sound_played, result.protocol.name)
                is SoundSession.Result.Failed -> getString(R.string.sound_failed, result.reason)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            render()
        }.also { it.start() }
        render()
    }

    // ---- rendering ----

    private fun render() {
        val now = SystemClock.elapsedRealtime()
        if (pin == null) {
            listRoot.visibility = View.VISIBLE
            finderRoot.visibility = View.GONE
            renderList(now)
        } else {
            listRoot.visibility = View.GONE
            finderRoot.visibility = View.VISIBLE
            renderFinder(now)
        }
    }

    private fun renderList(now: Long) {
        deviceListView.removeAllViews()
        val devices = nearby.sorted()
        if (devices.isEmpty()) {
            deviceListView.addView(text(getString(R.string.nothing_found)))
            return
        }
        devices.forEach { deviceListView.addView(deviceRow(it, now)) }
    }

    private fun deviceRow(device: NearbyDevices.Device, now: Long): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { pinDevice(device) }
        }
        row.addView(text(device.title, sizeSp = 19f, isBold = true))
        val warmth = device.meter.warmth(now)
        val rssi = device.meter.smoothedRssi?.let { "%.0f dBm".format(it) } ?: "—"
        val metres = device.meter.approximateMetres()?.let { " · ~%.1f m".format(it) } ?: ""
        row.addView(text("${warmth.label} · $rssi$metres", sizeSp = 14f))
        row.addView(text(detailFor(device.kind), sizeSp = 13f))
        row.addView(text(getString(R.string.tap_to_find), sizeSp = 12f).apply { setTextColor(ACCENT) })
        return row
    }

    private fun renderFinder(now: Long) {
        when (val result = PinnedSelection.resolve(nearby, pin)) {
            is PinnedSelection.Result.Tracking -> showTracking(result.device, now)
            PinnedSelection.Result.SignalLost -> showSignalLost()
            PinnedSelection.Result.Unpinned -> unpin()
        }
    }

    private fun showTracking(device: NearbyDevices.Device, now: Long) {
        finderTitle.text = device.title
        val rssi = device.meter.smoothedRssi
        val warmth = device.meter.warmth(now)
        val lost = warmth == ProximityMeter.Warmth.LOST

        meter.active = !lost
        meter.level = levelFor(rssi, lost)
        haptics?.update(if (lost) null else rssi)

        val trend = when (device.meter.trend()) {
            ProximityMeter.Trend.WARMER -> "  ↑"
            ProximityMeter.Trend.COLDER -> "  ↓"
            else -> ""
        }
        warmthView.text = warmth.label + trend
        warmthView.setTextColor(if (lost) HINT else ACCENT)
        distanceView.text = when {
            lost -> getString(R.string.move_around)
            else -> device.meter.approximateMetres()?.let { "~%.1f m".format(it) } ?: ""
        }
        detailView.text = detailFor(device.kind)

        if (device.canPlaySound) {
            finderSoundButton.visibility = View.VISIBLE
            finderSoundButton.isEnabled = activeSound == null
            finderSoundButton.text =
                getString(if (soundTargetAddress == device.address) R.string.playing else R.string.play_sound)
            finderSoundButton.setOnClickListener { playSound(device.address) }
        } else {
            finderSoundButton.visibility = View.GONE
        }
        prefs.saveLastStatus(device.title, detailFor(device.kind))
    }

    private fun showSignalLost() {
        meter.active = false
        meter.level = 0f
        haptics?.update(null)
        finderTitle.text = pin?.title ?: ""
        warmthView.text = getString(R.string.signal_lost)
        warmthView.setTextColor(HINT)
        distanceView.text = getString(R.string.move_around)
        detailView.text = ""
        finderSoundButton.visibility = View.GONE
    }

    /** Maps RSSI to a 0..1 gauge fill across the useful search range. */
    private fun levelFor(rssi: Double?, lost: Boolean): Float {
        if (lost || rssi == null) return 0f
        val fraction = (rssi - HapticCadence.SILENT_BELOW_DBM) /
            (HapticCadence.NEAR_DBM - HapticCadence.SILENT_BELOW_DBM)
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }

    private fun detailFor(kind: NearbyDevices.Kind): String = when (kind) {
        is NearbyDevices.Kind.AirPodsNearby -> {
            val s = kind.status
            "L ${pod(s.left)}   R ${pod(s.right)}   Case ${pod(s.case)}"
        }
        is NearbyDevices.Kind.FindMy -> {
            val separation = if (kind.status.isSeparated) "separated from owner" else "owner nearby"
            val battery = kind.status.battery.name.lowercase().replace('_', ' ')
            if (kind.status.deviceType == OfflineFinding.DeviceType.APPLE_DEVICE) separation
            else "Find My · $separation · battery $battery"
        }
    }

    private fun pod(pod: ProximityPairing.Pod): String {
        val battery = pod.batteryPercent?.let { "$it%" } ?: "–"
        val flags = buildString {
            if (pod.isCharging) append("⚡")
            if (pod.isInEar) append("👂")
        }
        return battery + flags
    }

    // ---- layout ----

    private fun buildLayout(): View {
        val container = FrameLayout(this)
        listRoot = buildListRoot()
        finderRoot = buildFinderRoot()
        container.addView(listRoot)
        container.addView(finderRoot)
        return container
    }

    private fun buildListRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
        }
        root.addView(text(getString(R.string.app_name), sizeSp = 24f, isBold = true))
        root.addView(text(getString(R.string.intro), sizeSp = 13f).apply { setTextColor(HINT) })
        statusView = text(getString(R.string.scanning), sizeSp = 13f).apply { setTextColor(HINT) }
        root.addView(statusView)
        permissionButton = Button(this).apply {
            setText(R.string.grant_permission)
            visibility = View.GONE
            setOnClickListener { requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST) }
        }
        root.addView(permissionButton)
        deviceListView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(deviceListView)
        return ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        }
    }

    private fun buildFinderRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(20), dp(16), dp(20), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val back = Button(this).apply {
            setText(R.string.back)
            setOnClickListener { unpin() }
        }
        finderTitle = text("", sizeSp = 20f, isBold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            setPadding(dp(12), 0, 0, 0)
        }
        header.addView(back)
        header.addView(finderTitle)
        root.addView(header)

        // Meter with centered overlay text.
        val meterWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(12) }
        }
        meter = SignalMeterView(this)
        meterWrap.addView(meter, FrameLayout.LayoutParams(MATCH, MATCH))
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        warmthView = text("", sizeSp = 40f, isBold = true).apply { gravity = Gravity.CENTER }
        distanceView = text("", sizeSp = 22f).apply {
            gravity = Gravity.CENTER
            setTextColor(HINT)
        }
        overlay.addView(warmthView)
        overlay.addView(distanceView)
        meterWrap.addView(overlay)
        root.addView(meterWrap)

        detailView = text("", sizeSp = 15f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(detailView)

        finderSoundButton = Button(this).apply {
            setText(R.string.play_sound)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(finderSoundButton)
        return root
    }

    private fun text(value: String, sizeSp: Float = 16f, isBold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        if (isBold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PERMISSION_REQUEST = 1
        const val REFRESH_MILLIS = 400L
        const val EXTRA_FIND_PINNED = "dev.podfinder.FIND_PINNED"
        val ACCENT = Color.parseColor("#2E6BE6")
        val HINT = Color.parseColor("#8A8A8E")
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    }
}

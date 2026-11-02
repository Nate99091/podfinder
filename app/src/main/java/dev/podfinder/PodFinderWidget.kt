package dev.podfinder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget: shows the pinned device and its last-seen status, and opens PodFinder (into
 * the finder for the pinned device) on tap. Status refreshes whenever the app is foregrounded;
 * the widget itself doesn't scan, so it stays battery-cheap.
 */
class PodFinderWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PodFinderWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val store = PinStore(context)
            val title = store.lastTitle ?: context.getString(R.string.widget_no_pin)
            val detail = store.lastDetail ?: context.getString(R.string.widget_tap_to_open)

            val launch = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("dev.podfinder.FIND_PINNED", true)
            }
            val pending = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_podfinder).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_detail, detail)
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
        }
    }
}

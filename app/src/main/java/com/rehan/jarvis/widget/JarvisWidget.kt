package com.rehan.jarvis.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rehan.jarvis.MainActivity
import com.rehan.jarvis.R
import com.rehan.jarvis.core.Intents

/**
 * Home screen widget — ek tap me Jarvis sunna shuru kar deta hai.
 * Orb pe tap = sunna shuru, baaki jagah tap = app khulti hai.
 */
class JarvisWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_jarvis)

            views.setOnClickPendingIntent(R.id.widget_orb, listenIntent(context))
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context))
            views.setOnClickPendingIntent(R.id.widget_label, listenIntent(context))

            return views
        }

        /** Widget tap → app khule aur turant sunna shuru kare. */
        private fun listenIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intents.EXTRA_START_LISTENING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Intents.EXTRA_START_LISTENING, true)
            return PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Widget ko refresh karo (state badalne pe kaam aata hai). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, JarvisWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context))
        }
    }
}

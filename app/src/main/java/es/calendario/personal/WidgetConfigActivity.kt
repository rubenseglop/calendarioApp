package es.calendario.personal

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class WidgetConfigActivity : Activity() {
    companion object {
        const val PREFS = "calendar_widget_preferences"
        fun opacityKey(widgetId: Int) = "widget_opacity_$widgetId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getInt(opacityKey(widgetId), 88)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 20)
        }
        val title = TextView(this).apply {
            text = "Apariencia del widget"
            textSize = 20f
        }
        val value = TextView(this).apply {
            text = "Opacidad: $current%"
            textSize = 16f
            setPadding(0, 22, 0, 8)
        }
        val seek = SeekBar(this).apply {
            max = 100
            progress = current
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                value.text = "Opacidad: $progress%"
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        val help = TextView(this).apply {
            text = "Puedes ajustar la transparencia de cada widget por separado."
            textSize = 13f
            setPadding(0, 8, 0, 20)
        }
        val save = Button(this).apply {
            text = "Guardar widget"
            setOnClickListener {
                prefs.edit().putInt(opacityKey(widgetId), seek.progress).apply()
                WidgetUpdater.refresh(this@WidgetConfigActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }
        root.addView(title)
        root.addView(value)
        root.addView(seek)
        root.addView(help)
        root.addView(save)
        setContentView(root)
    }
}

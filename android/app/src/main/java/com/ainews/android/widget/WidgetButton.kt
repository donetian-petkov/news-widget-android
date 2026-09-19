package com.ainews.android.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.ColorFilter
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ainews.android.MainActivity
import com.ainews.android.R

@androidx.compose.runtime.Composable
fun WidgetIconButton(
    iconRes: Int,
    contentDescription: String,
    action: Action,
    backgroundRes: Int = R.drawable.widget_button_light,
    tint: Color? = null,
    compact: Boolean = false,
) {
    Box(
        modifier = GlanceModifier
            .padding(end = if (compact) 6.dp else 8.dp)
            .background(ImageProvider(backgroundRes))
            .cornerRadius(if (compact) 9.dp else 12.dp)
            .clickable(action)
            .padding(if (compact) 5.dp else 7.dp),
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            colorFilter = tint?.let { ColorFilter.tint(ColorProvider(it)) },
            modifier = GlanceModifier.size(if (compact) 14.dp else 18.dp),
        )
    }
}

fun shareFromWidget(context: Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ContextCompat.startActivity(
        context,
        Intent.createChooser(intent, "Share story").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        null,
    )
}

fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

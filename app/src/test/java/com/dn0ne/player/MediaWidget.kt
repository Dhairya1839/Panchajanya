import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class MediaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF1E293B), night = androidx.compose.ui.graphics.Color(0xFF0F172A))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Info
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = "Song Title",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(androidx.compose.ui.graphics.Color.White)
                    )
                )
                Text(
                    text = "Artist Name",
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color.LightGray)
                    )
                )
            }

            // Media Controls Row (Previous, Play/Pause, Next)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏮",
                    style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color.White)),
                    modifier = GlanceModifier.padding(8.dp).clickable { /* Send Skip Previous Broadcast */ }
                )
                Text(
                    text = "⏯",
                    style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color.White)),
                    modifier = GlanceModifier.padding(8.dp).clickable { /* Send Play/Pause Broadcast */ }
                )
                Text(
                    text = "⏭",
                    style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color.White)),
                    modifier = GlanceModifier.padding(8.dp).clickable { /* Send Skip Next Broadcast */ }
                )
            }
        }
    }
}

class MediaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediaWidget()
}

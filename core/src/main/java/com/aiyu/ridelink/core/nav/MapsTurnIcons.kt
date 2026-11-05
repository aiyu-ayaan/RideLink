package com.aiyu.ridelink.core.nav

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turn type from a Google Maps navigation notification's large icon, matched against the `maneuver_*` drawables
 * of the installed Maps app (see [ManeuverIcon]). Needs the Maps `<queries>` entry in the manifest.
 */
@Singleton
class MapsTurnIcons @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var templates: List<ManeuverIcon.Template> = emptyList()
    private var lastIcon: Bitmap? = null
    private var lastMatch: ManeuverIcon.Match? = null

    /** Null when there is no icon, Maps' drawables cannot be read, or nothing matches well enough. */
    @Synchronized
    fun classify(icon: Icon?): ManeuverIcon.Match? {
        val bitmap = icon?.let(::bitmapOf) ?: return null
        // Maps reposts the same arrow every second while the distance counts down.
        if (lastIcon?.sameAs(bitmap) == true) return lastMatch
        if (templates.isEmpty()) templates = load()
        val grid = ManeuverIcon.grid(pixels(bitmap), bitmap.width, bitmap.height)
        val match = grid?.let { ManeuverIcon.match(it, templates) }
        lastIcon = bitmap
        lastMatch = match
        return match
    }

    /** How many Maps turn drawables were found; 0 means Maps is missing or renamed them in an update. */
    val templateCount: Int get() = templates.size

    @SuppressLint("DiscouragedApi") // Maps' resource ids change with every Maps build; only the names are stable.
    private fun load(): List<ManeuverIcon.Template> {
        val res = runCatching { context.packageManager.getResourcesForApplication(MapsNotificationParser.MAPS_PACKAGE) }
            .getOrNull() ?: return emptyList()
        return ManeuverIcon.MAPS_DRAWABLES.mapNotNull { name ->
            val id = res.getIdentifier(name, "drawable", MapsNotificationParser.MAPS_PACKAGE)
            val drawable = if (id == 0) null else runCatching { res.getDrawable(id, null) }.getOrNull()
            val bitmap = drawable?.let { draw(it, TEMPLATE_PX, TEMPLATE_PX) } ?: return@mapNotNull null
            ManeuverIcon.grid(pixels(bitmap), TEMPLATE_PX, TEMPLATE_PX)
                ?.let { ManeuverIcon.Template(name, ManeuverIcon.maneuverFor(name), it) }
        }
    }

    private fun bitmapOf(icon: Icon): Bitmap? {
        val d = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return null
        val b = (d as? BitmapDrawable)?.bitmap ?: return draw(d, d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1))
        return if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
    }

    private fun draw(d: Drawable, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { d.setBounds(0, 0, w, h); d.draw(Canvas(it)) }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private companion object {
        const val TEMPLATE_PX = 96
    }
}

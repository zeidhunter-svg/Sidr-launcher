package com.sidr.launcher.feature.launcher

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── App-icon loading — shared by the home Favorites row and the App Drawer ──
// PackageManager / Drawable is a feature concern: it must NOT leak into core/ui (which is why
// AppTile takes the icon as a composable slot). These helpers are internal so both LauncherScreen
// and AppDrawerScreen in this module can reuse them.

/**
 * Loads an app's launcher icon off the main thread (no synchronous PackageManager call in
 * composition). Returns a [State] that starts null and updates to the decoded bitmap, or stays
 * null if the package has no resolvable icon.
 */
@Composable
internal fun rememberAppIcon(packageName: String): State<ImageBitmap?> {
    val pm = LocalContext.current.packageManager
    val state = remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            state.value = try {
                pm.getApplicationIcon(packageName).toImageBitmap()
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    return state
}

// TODO: handle AdaptiveIconDrawable (intrinsicWidth/Height = -1) — move to a dedicated
//       image-loading layer (e.g. Coil + AppIconFetcher) in a later phase.
internal fun Drawable.toImageBitmap(): ImageBitmap {
    val bmp = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

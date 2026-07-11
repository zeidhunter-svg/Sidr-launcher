package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Shape
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Strokes

/** 2px accent focus ring shown only while focused (grey spec §4: 2px is focus/risk only). */
fun Modifier.sidrFocusRing(shape: Shape = SidrShapes.small): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused) Modifier.border(Strokes.focus, SidrTheme.colors.accentBorder, shape) else Modifier
    this.onFocusEvent { focused = it.isFocused }.then(ring)
}

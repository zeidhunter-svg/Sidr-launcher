package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.core.ui.theme.Strokes

/**
 * DS-3 button family (spec §5.1). Deep controls, small interfaces: each public composable hides layout,
 * token choice, focus, semantics, disabled/loading behaviour behind a small caller interface.
 *
 * Rules (spec §5.1):
 * - 48dp minimum touch target;
 * - 8-10dp visual radius, never pill by default;
 * - loading disables duplicate taps and exposes progress semantics;
 * - destructive is muted border/text, not a loud red fill;
 * - terminal action is reserved for compact system/action surfaces, not normal Settings rows.
 *
 * Presentation-only: no domain/data/feature imports.
 */

/**
 * Primary action button - filled accent surface, the one clear next step on a surface.
 */
@Composable
fun SidrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable (() -> Unit))? = null,
) = SidrButtonFrame(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    leadingIcon = leadingIcon,
    container = SidrTheme.colors.accent,
    content = SidrTheme.colors.ground,
    disabledContainer = SidrTheme.colors.raised,
    disabledContent = SidrTheme.colors.faint,
    useOutlined = false,
)

/**
 * Secondary action - outlined, quieter than primary but still a deliberate action.
 */
@Composable
fun SidrSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable (() -> Unit))? = null,
) = SidrButtonFrame(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    leadingIcon = leadingIcon,
    container = Color.Transparent,
    content = SidrTheme.colors.text,
    disabledContainer = Color.Transparent,
    disabledContent = SidrTheme.colors.faint,
    useOutlined = true,
)

/**
 * Tertiary action - text-only, the quietest action affordance (e.g. "Not now").
 */
@Composable
fun SidrTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) = SidrButtonFrame(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    leadingIcon = null,
    container = Color.Transparent,
    content = SidrTheme.colors.dim,
    disabledContainer = Color.Transparent,
    disabledContent = SidrTheme.colors.faint,
    useOutlined = false,
    isText = true,
)

/**
 * Destructive action - muted border + danger text, never a loud red fill (spec §5.1).
 */
@Composable
fun SidrDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) = SidrButtonFrame(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    leadingIcon = null,
    container = Color.Transparent,
    content = SidrTheme.colors.danger,
    disabledContainer = Color.Transparent,
    disabledContent = SidrTheme.colors.faint,
    useOutlined = true,
    outline = SidrTheme.colors.danger,
)

/**
 * Terminal action - compact bracketed `[ label ]` accelerator for system/action surfaces.
 * Reserved for compact surfaces, not normal Settings rows (spec §5.1). Press-invert on [selected].
 */
@Composable
fun SidrTerminalAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = SidrTheme.colors
    val accent = colors.accent
    val fg = if (selected) colors.ground else accent
    SidrText(
        text = "[ $text ]",
        role = SidrTextRole.SYSTEM,
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .wrapContentHeight(Alignment.CenterVertically)
            .wrapContentWidth()
            .padding(horizontal = Spacing.sm)
            .semantics { role = Role.Button }
            .let { base ->
                if (enabled) base.clickable(
                    onClickLabel = text,
                    role = Role.Button,
                    onClick = onClick,
                ) else base
            },
        color = if (enabled) fg else colors.faint,
    )
}

/**
 * Shared internal button frame: shape, min touch target, loading guard, semantics.
 * Loading disables the click path so duplicate taps are impossible (spec §5.1).
 */
@Composable
private fun SidrButtonFrame(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    leadingIcon: (@Composable (() -> Unit))?,
    container: Color,
    content: Color,
    disabledContainer: Color,
    disabledContent: Color,
    useOutlined: Boolean = false,
    isText: Boolean = false,
    outline: Color? = null,
) {
    val effectiveEnabled = enabled && !loading
    val colors = ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = content,
        disabledContainerColor = disabledContainer,
        disabledContentColor = disabledContent,
    )
    val outlinedColors = ButtonDefaults.outlinedButtonColors(
        contentColor = content,
        disabledContentColor = disabledContent,
    )
    val borderColor = outline ?: SidrTheme.colors.border

    val click: () -> Unit = if (effectiveEnabled) onClick else ({})

    val innerContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                leadingIcon?.invoke()
            }
            Text(text = text, color = content)
        }
    }

    val minTarget = Modifier.heightIn(min = Sizes.minTouchTarget)

    when {
        isText -> TextButton(
            onClick = click,
            modifier = modifier.then(minTarget).wrapContentHeight(Alignment.CenterVertically),
            enabled = effectiveEnabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = content,
                disabledContentColor = disabledContent,
            ),
            content = { innerContent() },
        )
        useOutlined -> OutlinedButton(
            onClick = click,
            modifier = modifier.then(minTarget),
            enabled = effectiveEnabled,
            shape = SidrShapes.medium,
            colors = outlinedColors,
            border = BorderStroke(
                Strokes.hairline,
                if (effectiveEnabled) borderColor else SidrTheme.colors.line,
            ),
            content = { innerContent() },
        )
        else -> Button(
            onClick = click,
            modifier = modifier.then(minTarget),
            enabled = effectiveEnabled,
            shape = SidrShapes.medium,
            colors = colors,
            content = { innerContent() },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrButtonPreview() {
    SidrTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SidrPrimaryButton("Continue", onClick = {})
            SidrSecondaryButton("Not now", onClick = {})
            SidrTertiaryButton("Dismiss", onClick = {})
            SidrDestructiveButton("Forget", onClick = {})
            SidrTerminalAction("CONFIRM", onClick = {})
            SidrTerminalAction("SELECTED", onClick = {}, selected = true)
            SidrPrimaryButton("Loading", onClick = {}, loading = true)
            SidrPrimaryButton("Disabled", onClick = {}, enabled = false)
        }
    }
}

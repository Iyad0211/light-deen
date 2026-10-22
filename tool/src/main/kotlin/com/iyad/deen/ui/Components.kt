package com.iyad.deen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.iyad.deen.R
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

val QuranFont = FontFamily(Font(R.font.amiri_quran))
val ArabicFont = FontFamily(Font(R.font.amiri))

/** Theme wrapper every screen uses, so the whole tool follows the app theme setting. */
@Composable
fun DeenTheme(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors, content = content)
}

/**
 * A full screen with the standard LightOS top bar (back on the left, title in the middle).
 */
@Composable
fun DeenScreen(
    title: String,
    onBack: (() -> Unit)?,
    right: LightTopBarButton? = null,
    background: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DeenTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(background ?: LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                center = LightTopBarCenter.Text(title),
                rightButton = right,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content,
            )
        }
    }
}

/** A tappable list row in the LightOS style: big plain words, optional detail underneath. */
@Composable
fun MenuRow(
    label: String,
    detail: String? = null,
    variant: LightTextVariant = LightTextVariant.Heading,
    trailing: String? = null,
    icon: LightIconConfiguration? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.lightClickable(onClick = onClick) else it }
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            LightText(label, variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) LightText(detail, LightTextVariant.Detail, lighten = true)
        }
        if (trailing != null) {
            LightText(trailing, LightTextVariant.Copy, lighten = icon == null, modifier = Modifier.padding(start = 1f.gridUnitsAsDp()))
        }
        if (icon != null) {
            LightIcon(icon = icon, modifier = Modifier.padding(start = 1f.gridUnitsAsDp()))
        }
    }
}

/** A setting with an on/off toggle drawn with the LightOS toggle icons. */
@Composable
fun ToggleRow(label: String, detail: String? = null, checked: Boolean, onToggle: (Boolean) -> Unit) {
    MenuRow(
        label = label,
        detail = detail,
        variant = LightTextVariant.Copy,
        icon = if (checked) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
        onClick = { onToggle(!checked) },
    )
}

@Composable
fun SectionLabel(text: String) {
    LightText(
        text.uppercase(),
        LightTextVariant.Superfine,
        lighten = true,
        modifier = Modifier.padding(start = 2f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
    )
}

@Composable
fun CenteredMessage(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LightText(text, LightTextVariant.Copy, align = TextAlign.Center)
            if (action != null && onAction != null) {
                LightText(
                    action,
                    LightTextVariant.Button,
                    underline = true,
                    modifier = Modifier
                        .padding(top = 1.5f.gridUnitsAsDp())
                        .lightClickable(onClick = onAction),
                )
            }
        }
    }
}

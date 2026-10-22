package com.iyad.deen.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.iyad.deen.core.Content
import com.iyad.deen.core.Dhikr
import com.iyad.deen.ui.ArabicFont
import com.iyad.deen.ui.CenteredMessage
import com.iyad.deen.ui.DeenScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AzkarViewModel(ctx: SealedLightContext, private val morning: Boolean) : PlaceViewModel(ctx) {
    val items = MutableStateFlow<List<Dhikr>?>(null)

    init {
        viewModelScope.launch {
            items.value = withContext(Dispatchers.IO) {
                Content.azkar(ctx::readAsset)[if (morning) "morning" else "evening"].orEmpty()
            }
        }
    }
}

class AzkarScreen(
    private val sealedActivity: SealedLightActivity,
    private val morning: Boolean,
) : LightScreen<Unit, AzkarViewModel>(sealedActivity) {
    override val viewModelClass = AzkarViewModel::class.java
    override fun createViewModel() = AzkarViewModel(lightContext, morning)

    @Composable
    override fun Content() {
        val items by viewModel.items.collectAsState()
        val s by viewModel.settings.collectAsState()
        var index by remember { mutableIntStateOf(0) }
        val title = if (morning) "Morning azkar" else "Evening azkar"

        DeenScreen(
            title,
            onBack = { goBack() },
            right = items?.let { LightBarButton.Text("${(index + 1).coerceAtMost(it.size)}/${it.size}", onClick = null) },
        ) {
            val list = items
            if (list == null || s == null) {
                CenteredMessage("Opening…")
                return@DeenScreen
            }
            val remaining = remember(list) { mutableStateListOf<Int>().apply { addAll(list.map { it.count }) } }

            if (index >= list.size) {
                CenteredMessage(
                    "تقبّل الله\n\nYou've finished the ${if (morning) "morning" else "evening"} azkar.",
                    "Start again",
                ) {
                    remaining.clear(); remaining.addAll(list.map { it.count }); index = 0
                }
                return@DeenScreen
            }

            val d = list[index]
            val left = remaining[index]

            // auto-advance a moment after the last repetition
            LaunchedEffect(index, left) {
                if (left == 0) {
                    delay(450)
                    index++
                }
            }

            LightScrollView(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 1.5f.gridUnitsAsDp()),
                scrollState = remember(index) { androidx.compose.foundation.ScrollState(0) },
            ) {
                Text(
                    d.arabic,
                    style = TextStyle(
                        fontFamily = ArabicFont,
                        fontSize = 27.sp,
                        lineHeight = 50.sp,
                        color = LightThemeTokens.colors.content,
                        textAlign = TextAlign.Center,
                        textDirection = TextDirection.Rtl,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp()),
                )
                if (s?.showTranslation == true) {
                    LightText(
                        d.english,
                        LightTextVariant.Detail,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()),
                    )
                }
                if (d.reference.isNotBlank()) {
                    LightText(
                        d.reference,
                        LightTextVariant.Superfine,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 0.75f.gridUnitsAsDp()),
                    )
                }
            }

            // counter: the bottom of the screen is one big button
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LightText(
                    "‹",
                    LightTextVariant.Subtitle,
                    lighten = index == 0,
                    modifier = Modifier.lightClickable(enabled = index > 0) { index-- }.padding(end = 1.5f.gridUnitsAsDp()),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(5f.gridUnitsAsDp())
                        .lightClickable(enabled = left > 0) {
                            remaining[index] = left - 1
                            if (left - 1 == 0) sealedActivity.vibrator.click()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LightText(if (left > 0) "$left" else "✓", LightTextVariant.Subtitle)
                        LightText(
                            if (d.count > 1) "tap · ${d.count} times" else "tap when done",
                            LightTextVariant.Superfine,
                            lighten = true,
                        )
                    }
                }
                LightText(
                    "›",
                    LightTextVariant.Subtitle,
                    modifier = Modifier.lightClickable { index++ }.padding(start = 1.5f.gridUnitsAsDp()),
                )
            }
        }
    }
}

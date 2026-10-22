package com.iyad.deen.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.iyad.deen.core.Content
import com.iyad.deen.core.JUZ_STARTS
import com.iyad.deen.core.ReaderTheme
import com.iyad.deen.core.SettingsStore
import com.iyad.deen.core.Surah
import com.iyad.deen.core.juzOf
import com.iyad.deen.core.toArabicDigits
import com.iyad.deen.ui.ArabicFont
import com.iyad.deen.ui.CenteredMessage
import com.iyad.deen.ui.DeenScreen
import com.iyad.deen.ui.QuranFont
import com.iyad.deen.ui.SectionLabel
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightColors
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

fun readerColors(t: ReaderTheme): LightColors = when (t) {
    ReaderTheme.Black -> LightColors(Color.Black, Color(0xFFF2F2F2), Color(0xFF9A9A9A))
    ReaderTheme.Light -> LightColors(Color.White, Color(0xFF111111), Color(0xFF6B6B6B))
    ReaderTheme.Cream -> LightColors(Color(0xFFF5ECD7), Color(0xFF2E2416), Color(0xFF7A6A52))
}

open class QuranViewModel(ctx: SealedLightContext) : PlaceViewModel(ctx) {
    val quran = MutableStateFlow<List<Surah>?>(null)

    init {
        viewModelScope.launch {
            quran.value = withContext(Dispatchers.IO) { Content.quran(ctx::readAsset) }
        }
    }
}

/* ------------------------------------------------------------------ index */

class QuranIndexScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, QuranViewModel>(sealedActivity) {
    override val viewModelClass = QuranViewModel::class.java
    override fun createViewModel() = QuranViewModel(lightContext)

    @Composable
    override fun Content() {
        val quran by viewModel.quran.collectAsState()
        val s by viewModel.settings.collectAsState()
        var byJuz by remember { mutableStateOf(false) }

        DeenScreen(
            "Quran",
            onBack = { goBack() },
            right = LightBarButton.Text(if (byJuz) "Surah" else "Juz", onClick = { byJuz = !byJuz }),
        ) {
            val list = quran
            if (list == null) {
                CenteredMessage("Opening the Quran…")
                return@DeenScreen
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                val last = s?.takeIf { it.lastSurah in 1..114 }
                if (last != null) {
                    item {
                        val su = list[last.lastSurah - 1]
                        IndexRow(
                            title = "Continue reading",
                            detail = "${su.transliteration} · ayah ${last.lastAyah}",
                            arabic = su.arabicName,
                        ) { open(last.lastSurah, last.lastAyah) }
                    }
                }
                if (byJuz) {
                    itemsIndexed(JUZ_STARTS) { i, (sn, an) ->
                        val su = list[sn - 1]
                        IndexRow(
                            title = "Juz ${i + 1}",
                            detail = "${su.transliteration} ${sn}:${an}",
                            arabic = "الجزء ${(i + 1).toArabicDigits()}",
                        ) { open(sn, an) }
                    }
                } else {
                    itemsIndexed(list) { _, su ->
                        IndexRow(
                            title = "${su.number}. ${su.transliteration}",
                            detail = "${su.english} · ${su.verses.size} ayat",
                            arabic = su.arabicName,
                        ) { open(su.number, 1) }
                    }
                }
            }
        }
    }

    private fun open(surah: Int, ayah: Int) {
        navigateTo({ a -> QuranReaderScreen(a, surah, ayah) })
    }
}

@Composable
private fun IndexRow(title: String, detail: String, arabic: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.45f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LightText(title, LightTextVariant.Copy, maxLines = 1)
            LightText(detail, LightTextVariant.Detail, lighten = true, maxLines = 1)
        }
        Text(
            arabic,
            style = TextStyle(fontFamily = ArabicFont, fontSize = 26.sp, color = LightThemeTokens.colors.content),
            modifier = Modifier.padding(start = 1f.gridUnitsAsDp()),
        )
    }
}

/* ----------------------------------------------------------------- reader */

class QuranReaderViewModel(ctx: SealedLightContext) : QuranViewModel(ctx) {
    fun saveProgress(surah: Int, ayah: Int) {
        viewModelScope.launch { SettingsStore.update(ctx.dataStore) { lastRead(surah, ayah) } }
    }
}

class QuranReaderScreen(
    sealedActivity: SealedLightActivity,
    private val startSurah: Int,
    private val startAyah: Int,
) : LightScreen<Unit, QuranReaderViewModel>(sealedActivity) {
    override val viewModelClass = QuranReaderViewModel::class.java
    override fun createViewModel() = QuranReaderViewModel(lightContext)

    @OptIn(FlowPreview::class)
    @Composable
    override fun Content() {
        val quran by viewModel.quran.collectAsState()
        val s by viewModel.settings.collectAsState()
        var surahNo by remember { mutableStateOf(startSurah) }
        var jumpTo by remember { mutableStateOf(startAyah) }
        var showOptions by remember { mutableStateOf(false) }

        val settings = s
        val list = quran
        val theme = settings?.readerTheme ?: ReaderTheme.Cream
        val colors = readerColors(theme)

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(colors.background)) {
                val surah = list?.getOrNull(surahNo - 1)
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.TwoLineDetail(
                        surah?.transliteration ?: "Quran",
                        surah?.let { "Surah $surahNo · Juz ${juzOf(surahNo, jumpTo.coerceAtLeast(1))}" } ?: "",
                    ),
                    rightButton = LightBarButton.Text("Aa", onClick = { showOptions = !showOptions }),
                )
                if (showOptions && settings != null) {
                    ReaderOptions(
                        theme = theme,
                        size = settings.readerFontSize,
                        onTheme = { t -> viewModel.update({ readerTheme(t) }) },
                        onSize = { v -> viewModel.update({ readerFontSize(v.coerceIn(18, 56)) }) },
                    )
                }
                if (surah == null || settings == null) {
                    CenteredMessage("Opening…")
                    return@Column
                }

                val state = rememberLazyListState()
                // items: 0 = header, 1..n = ayat, n+1 = footer
                LaunchedEffect(surahNo) {
                    state.scrollToItem((jumpTo).coerceIn(1, surah.verses.size).let { if (it <= 1) 0 else it })
                }
                LaunchedEffect(surahNo) {
                    snapshotFlow { state.firstVisibleItemIndex }
                        .distinctUntilChanged()
                        .debounce(800)
                        .collect { idx ->
                            val ayah = idx.coerceIn(1, surah.verses.size)
                            jumpTo = ayah
                            viewModel.saveProgress(surahNo, ayah)
                        }
                }

                val fontSize = settings.readerFontSize.sp
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        item { SurahHeader(surah, colors) }
                        itemsIndexed(surah.verses) { i, verse ->
                            Text(
                                text = "$verse ۝${(i + 1).toArabicDigits()}",
                                style = TextStyle(
                                    fontFamily = QuranFont,
                                    fontSize = fontSize,
                                    lineHeight = fontSize * 1.9f,
                                    color = colors.content,
                                    textAlign = TextAlign.Justify,
                                    textDirection = TextDirection.Rtl,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 0.2f.gridUnitsAsDp()),
                            )
                        }
                        item {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                SurahFooter(list, surahNo) { n ->
                                    jumpTo = 1
                                    surahNo = n
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SurahHeader(surah: Surah, colors: LightColors) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 1f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "سُورَةُ ${surah.arabicName}",
                style = TextStyle(fontFamily = QuranFont, fontSize = 34.sp, color = colors.content, textAlign = TextAlign.Center),
            )
            LightText(
                "${surah.english} · ${if (surah.meccan) "Meccan" else "Medinan"} · ${surah.verses.size} ayat",
                LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
            )
            if (surah.number != 1 && surah.number != 9) {
                Text(
                    BASMALA,
                    style = TextStyle(fontFamily = QuranFont, fontSize = 30.sp, color = colors.content, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp()),
                )
            }
        }
    }

    @Composable
    private fun SurahFooter(list: List<Surah>, current: Int, onGo: (Int) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (current > 1) {
                LightText(
                    "‹ ${list[current - 2].transliteration}",
                    LightTextVariant.Copy,
                    modifier = Modifier.lightClickable { onGo(current - 1) },
                )
            } else Box(Modifier)
            if (current < 114) {
                LightText(
                    "${list[current].transliteration} ›",
                    LightTextVariant.Copy,
                    modifier = Modifier.lightClickable { onGo(current + 1) },
                )
            }
        }
    }
}

@Composable
private fun ReaderOptions(theme: ReaderTheme, size: Int, onTheme: (ReaderTheme) -> Unit, onSize: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp())) {
        SectionLabel("Background")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReaderTheme.entries.forEach { t ->
                LightText(
                    t.label,
                    LightTextVariant.Copy,
                    underline = t == theme,
                    lighten = t != theme,
                    modifier = Modifier.lightClickable { onTheme(t) }.padding(vertical = 0.3f.gridUnitsAsDp()),
                )
            }
        }
        SectionLabel("Text size")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText("A −", LightTextVariant.Heading, modifier = Modifier.lightClickable { onSize(size - 2) })
            LightText("$size", LightTextVariant.Copy, lighten = true)
            LightText("A +", LightTextVariant.Heading, modifier = Modifier.lightClickable { onSize(size + 2) })
        }
    }
}

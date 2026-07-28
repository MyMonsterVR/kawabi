package com.mymonstervr.kawabi.app.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.size.Size
import com.mymonstervr.kawabi.data.network.dto.PageDto
import com.mymonstervr.kawabi.data.network.resolveImageUrl
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

private enum class ReaderMode(val label: String) {
    VERTICAL("Vertical"),
    PAGED_LTR("Paged LTR"),
    PAGED_RTL("Paged RTL"),
}

// How long the reader waits for scrolling to settle before persisting lastPageRead.
// Without this, a fast scroll fires a DB write (+ trigger cascade) on every single
// index tick -- dozens of writes per second, which is what made scrolling janky.
private const val PROGRESS_WRITE_DEBOUNCE_MS = 400L

// A page counts as "reached" once its bottom edge is within this much of the viewport's
// bottom edge -- not only when it's pixel-perfect at the very bottom. Without slack, a
// device where the last image ends a hair short of the viewport (rounding, a sliver of
// next-chapter padding already composed) would never satisfy an exact equality check.
private val BOTTOM_REACHED_SLACK_DP = 48.dp

// How long the "now reading Chapter N" banner stays up after crossing into a new
// chapter during continuous vertical scrolling.
private const val CHAPTER_BANNER_DURATION_MS = 1800L

// A flat row in the continuous vertical list: either a page, or a small divider
// announcing the next chapter (matches kawabi-web's convention -- a label between
// chapters, not a screen transition).
private sealed interface FlatItem {
    val key: String
    data class PageItem(val sectionIndex: Int, val pageIndexInSection: Int, val page: PageDto) : FlatItem {
        override val key get() = "page:$sectionIndex:$pageIndexInSection"
    }
    data class ChapterDivider(val sectionIndex: Int, val label: String) : FlatItem {
        override val key get() = "divider:$sectionIndex"
    }
}

// True once the item at `index`'s bottom edge has scrolled up to within `slackPx` of the
// viewport's bottom edge -- i.e. you've actually seen the whole item, not just its top.
private fun androidx.compose.foundation.lazy.LazyListLayoutInfo.itemReachedBottom(index: Int, slackPx: Float): Boolean {
    val info = visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return info.offset + info.size <= viewportEndOffset + slackPx
}

private fun buildFlatItems(sections: List<ChapterSection>): List<FlatItem> = buildList {
    sections.forEachIndexed { sectionIndex, section ->
        if (sectionIndex > 0) add(FlatItem.ChapterDivider(sectionIndex, section.chapterLabel))
        section.pages.forEachIndexed { pageIndex, page ->
            add(FlatItem.PageItem(sectionIndex, pageIndex, page))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(
    chapterId: Long,
    onBack: () -> Unit,
    onNavigateChapter: (Long) -> Unit,
    viewModel: ReaderViewModel = koinViewModel(),
) {
    LaunchedEffect(chapterId) { viewModel.load(chapterId) }

    val state by viewModel.state.collectAsState()
    val isLoadingNext by viewModel.isLoadingNext.collectAsState()
    // Seeded from the Settings default at open time -- not observed reactively after
    // that, so changing the setting mid-read doesn't yank the current chapter into a
    // different mode out from under you. The in-reader mode-cycle button still overrides
    // per-session same as before.
    var mode by remember { mutableStateOf(viewModel.readingDirection.value.toReaderMode()) }
    var chromeVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(keepScreenAwake) {
        val window = (view.context as? android.app.Activity)?.window
        if (keepScreenAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
        when (val current = state) {
            is ReaderState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is ReaderState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(text = current.message, color = MaterialTheme.colorScheme.error)
            }
            is ReaderState.ErrorWithAlternate -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = current.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.swapToAlternate(current.alternateChapterId) }) {
                        Text(
                            "Read the ${current.alternateLabel} version instead?",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            is ReaderState.Success -> {
                if (mode == ReaderMode.VERTICAL) {
                    ContinuousVerticalScreen(
                        current = current,
                        isLoadingNext = isLoadingNext,
                        chromeVisible = chromeVisible,
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onPageChanged = viewModel::onPageChanged,
                        onTrackPosition = viewModel::trackPosition,
                        onNeedNext = viewModel::loadNextSection,
                    )
                } else {
                    PagedChapterScreen(
                        current = current,
                        mode = mode,
                        chromeVisible = chromeVisible,
                        scope = scope,
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onPageChanged = viewModel::onPageChanged,
                        onTrackPosition = viewModel::trackPosition,
                        onPrevChapter = { current.prevChapterId?.let(onNavigateChapter) },
                        onNextChapter = { current.nextChapterId?.let(onNavigateChapter) },
                    )
                }
            }
        }

        if (chromeVisible) {
            TopAppBar(
                title = { Text("Reader", color = androidx.compose.ui.graphics.Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = androidx.compose.ui.graphics.Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { mode = nextMode(mode) }) {
                        Text(mode.label, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                ),
            )
        }
    }
}

private fun ReadingDirection.toReaderMode(): ReaderMode = when (this) {
    ReadingDirection.LEFT_TO_RIGHT -> ReaderMode.PAGED_LTR
    ReadingDirection.RIGHT_TO_LEFT -> ReaderMode.PAGED_RTL
    ReadingDirection.VERTICAL -> ReaderMode.VERTICAL
}

private fun nextMode(mode: ReaderMode): ReaderMode = when (mode) {
    ReaderMode.VERTICAL -> ReaderMode.PAGED_LTR
    ReaderMode.PAGED_LTR -> ReaderMode.PAGED_RTL
    ReaderMode.PAGED_RTL -> ReaderMode.VERTICAL
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ContinuousVerticalScreen(
    current: ReaderState.Success,
    isLoadingNext: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onPageChanged: (chapterId: Long, index: Int, totalPages: Int, reachedEnd: Boolean) -> Unit,
    onTrackPosition: (chapterId: Long, index: Int, totalPages: Int, reachedEnd: Boolean) -> Unit,
    onNeedNext: () -> Unit,
) {
    val sections = current.sections
    val slackPx = with(LocalDensity.current) { BOTTOM_REACHED_SLACK_DP.toPx() }
    val flatItems = remember(sections) { buildFlatItems(sections) }
    // Section 0 (the chapter this screen was opened on) has no divider before it, so its
    // page indices map 1:1 onto flat indices -- current.startPage IS the flat index to
    // resume at. Unlike PagedChapterScreen's rememberPagerState(initialPage = ...), a
    // plain rememberLazyListState() defaults to index 0 with no equivalent parameter for
    // "start scrolled here" via a simple arg -- pass it explicitly or vertical mode always
    // opens at the top regardless of what's actually persisted.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.startPage)
    val scope = rememberCoroutineScope()
    var currentSectionIndex by remember { mutableIntStateOf(0) }
    var currentPageInSection by remember { mutableIntStateOf(current.startPage) }
    var bannerLabel by remember { mutableStateOf<String?>(null) }

    // flatItems/sections get a new identity every time loadNextSection() appends a
    // chapter (which fires early -- well before the user reaches the bottom of what's
    // loaded). Keying LaunchedEffect off them would restart this whole coroutine on
    // every append, cancelling the debounced write below before it ever gets an
    // uninterrupted 400ms to fire -- silently dropping progress for every chapter
    // except the very last one (confirmed live: only chapters with nothing after them
    // to trigger a preload ever had a non-zero last_page_read in the DB). Read the
    // latest values through rememberUpdatedState instead, so the collector loop itself
    // never restarts.
    val latestFlatItems by rememberUpdatedState(flatItems)
    val latestSections by rememberUpdatedState(sections)

    // Position tracking: the flat index maps back to (section, local page) via the same
    // list buildFlatItems just produced. Crossing into a later section means every
    // section strictly before it is finished (mirrors kawabi-web's onCurrent).
    LaunchedEffect(listState) {
        val positions = snapshotFlow { listState.firstVisibleItemIndex }
        launch {
            positions.collect { flatIndex ->
                val item = latestFlatItems.getOrNull(flatIndex) as? FlatItem.PageItem ?: return@collect
                if (item.sectionIndex > currentSectionIndex) {
                    for (i in currentSectionIndex until item.sectionIndex) {
                        val finished = latestSections[i]
                        onPageChanged(finished.chapterId, finished.pages.lastIndex, finished.pages.size, true)
                    }
                    currentSectionIndex = item.sectionIndex
                    bannerLabel = latestSections[item.sectionIndex].chapterLabel
                }
                currentPageInSection = item.pageIndexInSection
                val section = latestSections[item.sectionIndex]
                // Reached-end requires the last page's BOTTOM edge to actually be visible
                // (or nothing left below at all to scroll to) -- not just that its top has
                // scrolled into view, which used to fire the instant a tall webtoon strip's
                // final image appeared, long before its bottom was ever seen.
                val reachedEnd = item.pageIndexInSection == section.pages.lastIndex &&
                    (listState.layoutInfo.itemReachedBottom(flatIndex, slackPx) || !listState.canScrollForward)
                onTrackPosition(section.chapterId, item.pageIndexInSection, section.pages.size, reachedEnd)
                if (reachedEnd) {
                    onPageChanged(section.chapterId, item.pageIndexInSection, section.pages.size, true)
                }
            }
        }
        launch {
            positions.debounce(PROGRESS_WRITE_DEBOUNCE_MS).collect { flatIndex ->
                val item = latestFlatItems.getOrNull(flatIndex) as? FlatItem.PageItem ?: return@collect
                val section = latestSections[item.sectionIndex]
                val reachedEnd = item.pageIndexInSection == section.pages.lastIndex &&
                    (listState.layoutInfo.itemReachedBottom(flatIndex, slackPx) || !listState.canScrollForward)
                onPageChanged(section.chapterId, item.pageIndexInSection, section.pages.size, reachedEnd)
            }
        }
    }

    LaunchedEffect(bannerLabel) {
        if (bannerLabel != null) {
            delay(CHAPTER_BANNER_DURATION_MS)
            bannerLabel = null
        }
    }


    // Ask for the next chapter once scrolling nears the bottom of what's loaded so far
    // -- not only exactly at the bottom, so it's fetched well before the user gets there.
    LaunchedEffect(listState, current.hasMoreToAppend, isLoadingNext) {
        if (!current.hasMoreToAppend || isLoadingNext) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull() ?: return@snapshotFlow false
            lastVisible.index >= layout.totalItemsCount - 3
        }.collect { nearEnd -> if (nearEnd) onNeedNext() }
    }

    // Dragging the slider only seeks within the CURRENT chapter -- there's no single
    // fixed "total pages" across a continuously-growing list, so this resets (and the
    // slider's range changes) every time a chapter divider is crossed.
    fun seekWithinCurrentSection(localIndex: Int) {
        val section = sections.getOrNull(currentSectionIndex) ?: return
        val target = localIndex.coerceIn(0, section.pages.size - 1)
        val flatTarget = flatItems.indexOfFirst {
            it is FlatItem.PageItem && it.sectionIndex == currentSectionIndex && it.pageIndexInSection == target
        }
        if (flatTarget >= 0) {
            scope.launch { listState.scrollToItem(flatTarget) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Vertical is a continuous scroll -- no left/right page tap zones,
                    // any tap just toggles chrome.
                    detectTapGestures(onTap = { onToggleChrome() })
                },
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(flatItems, key = { it.key }) { item ->
                    when (item) {
                        // Capped, not unconditional fillMaxWidth -- a webtoon page
                        // stretched across a 10"+ tablet reads worse than one capped to a
                        // sane reading width and centered; a phone never hits this cap.
                        is FlatItem.PageItem -> {
                            // The min-height placeholder must only hold space open before
                            // the real image reports its size -- some sources (confirmed:
                            // MangaFire official chapters) split one tall panel across
                            // several page files, each individually much shorter than a
                            // normal page. A permanent floor here left dead black space
                            // below every such fragment once it *had* loaded, which read as
                            // "the image is split/missing a chunk" -- the actual content
                            // was just a sliver at the top of an oversized empty box.
                            var hasLoaded by remember(item.key) { mutableStateOf(false) }
                            AsyncImage(
                                model = pageImageRequest(resolveImageUrl(item.page.proxied_image_url)),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                onState = { state -> hasLoaded = state is AsyncImagePainter.State.Success },
                                modifier = Modifier.fillMaxWidth()
                                    .widthIn(max = READER_MAX_PAGE_WIDTH * LocalKawabiScale.current.spacing)
                                    .then(if (hasLoaded) Modifier else Modifier.defaultMinSize(minHeight = PAGE_PLACEHOLDER_MIN_HEIGHT)),
                            )
                        }
                        is FlatItem.ChapterDivider -> ChapterDividerRow(item.label)
                    }
                }
                if (isLoadingNext) {
                    item(key = "loading-next") {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        if (bannerLabel != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(100),
                color = NightSession.Chip,
                border = androidx.compose.foundation.BorderStroke(1.dp, NightSession.Hairline),
            ) {
                Text(
                    text = bannerLabel.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = NightSession.Text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (chromeVisible) {
            val section = sections.getOrNull(currentSectionIndex)
            if (section != null) {
                ChapterPageSlider(
                    currentPage = currentPageInSection,
                    totalPages = section.pages.size,
                    onSeek = ::seekWithinCurrentSection,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun ChapterDividerRow(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(NightSession.Background).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.3f), color = NightSession.Hairline)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = NightSession.Text)
    }
}

// Position within the CURRENT chapter only -- resets (and its range changes) each time
// a chapter divider is crossed. No '‹ ›' chapter-jump buttons here: in continuous mode
// chapters advance by scrolling/auto-append, not a manual jump.
@Composable
private fun ChapterPageSlider(currentPage: Int, totalPages: Int, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    var dragValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    val displayedPage = if (isDragging) dragValue.toInt() else currentPage

    Surface(modifier = modifier.fillMaxWidth(), color = NightSession.Chip) {
        Column {
            Slider(
                value = if (isDragging) dragValue else currentPage.toFloat(),
                onValueChange = { isDragging = true; dragValue = it },
                onValueChangeFinished = { isDragging = false; onSeek(dragValue.roundToInt()) },
                valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                steps = (totalPages - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = NightSession.Hairline,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Text(
                text = "${displayedPage + 1} / $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = NightSession.TextDim,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
private fun PagedChapterScreen(
    current: ReaderState.Success,
    mode: ReaderMode,
    chromeVisible: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onToggleChrome: () -> Unit,
    onPageChanged: (chapterId: Long, index: Int, totalPages: Int, reachedEnd: Boolean) -> Unit,
    onTrackPosition: (chapterId: Long, index: Int, totalPages: Int, reachedEnd: Boolean) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val section = current.sections.first()
    val pages = section.pages
    val pagerState = rememberPagerState(initialPage = current.startPage) { pages.size }
    var currentPage by remember(section.chapterId) { mutableIntStateOf(current.startPage) }
    // In paged mode the whole page is already visible on screen (ContentScale.Fit), so
    // arriving at the last page is a real "reached end" signal -- except when the chapter
    // opens with the last page already showing (a single-page chapter, or resuming a
    // chapter you'd already finished before). "Reached" there requires either genuinely
    // being a single-page chapter (nothing more to swipe to, so opening it IS reading it)
    // or having actually paged there rather than opened on it.
    val latestStartPage by rememberUpdatedState(current.startPage)

    LaunchedEffect(pagerState) {
        val positions = snapshotFlow { pagerState.currentPage }
        launch {
            positions.collect { index ->
                currentPage = index
                val reachedEnd = index == pages.lastIndex && (pages.size == 1 || index != latestStartPage)
                onTrackPosition(section.chapterId, index, pages.size, reachedEnd)
                if (reachedEnd) onPageChanged(section.chapterId, index, pages.size, true)
            }
        }
        launch {
            positions.debounce(PROGRESS_WRITE_DEBOUNCE_MS).collect { index ->
                val reachedEnd = index == pages.lastIndex && (pages.size == 1 || index != latestStartPage)
                onPageChanged(section.chapterId, index, pages.size, reachedEnd)
            }
        }
    }

    fun goToPage(index: Int, animate: Boolean = true) {
        val target = index.coerceIn(0, pages.size - 1)
        scope.launch {
            if (animate) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mode) {
                    detectTapGestures(onTap = { offset ->
                        val third = size.width / 3
                        when {
                            offset.x < third -> goToPage(currentPage - 1)
                            offset.x > third * 2 -> goToPage(currentPage + 1)
                            else -> onToggleChrome()
                        }
                    })
                },
        ) {
            HorizontalPager(
                state = pagerState,
                reverseLayout = mode == ReaderMode.PAGED_RTL,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                AsyncImage(
                    model = pageImageRequest(resolveImageUrl(pages[index].proxied_image_url)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (chromeVisible) {
            ReaderBottomBar(
                currentPage = currentPage,
                totalPages = pages.size,
                hasPrevChapter = current.prevChapterId != null,
                hasNextChapter = current.nextChapterId != null,
                onSeek = { index -> goToPage(index, animate = false) },
                onPrevChapter = onPrevChapter,
                onNextChapter = onNextChapter,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onSeek: (Int) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Slider drags fire onValueChange many times per gesture -- only track the dragged
    // value locally and commit the (expensive, animated) seek once on release, or the
    // scroll-position collector above fights the drag and the thumb jitters/stalls.
    var dragValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    // Only used for the page-count text below -- never fed back into the Slider's own
    // `value`, or rounding it there desyncs the Slider's internal drag-delta tracking
    // (feeding back a floor()'d value every frame made it stick near the drag's start).
    val displayedPage = if (isDragging) dragValue.toInt() else currentPage

    Surface(modifier = modifier.fillMaxWidth(), color = NightSession.Chip) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevChapter, enabled = hasPrevChapter) {
                Text("‹", color = if (hasPrevChapter) NightSession.Text else NightSession.TextDim)
            }
            Slider(
                value = if (isDragging) dragValue else currentPage.toFloat(),
                onValueChange = { isDragging = true; dragValue = it },
                onValueChangeFinished = { isDragging = false; onSeek(dragValue.roundToInt()) },
                valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                steps = (totalPages - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = NightSession.Hairline,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
                Text("›", color = if (hasNextChapter) NightSession.Text else NightSession.TextDim)
            }
        }
        Text(
            text = "${displayedPage + 1} / $totalPages",
            style = MaterialTheme.typography.labelSmall,
            color = NightSession.TextDim,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

// Placeholder height for a not-yet-loaded page. Without this, an AsyncImage with only
// fillMaxWidth() measures at ~0 height until Coil reports the real image's intrinsic
// size -- so scrollToItem() on a far-off, never-composed page computes its offset from
// a column of near-zero-height items and lands close to the top no matter the target.
// Not a hard aspectRatio: once the real image loads it's free to grow/shrink from here,
// so this doesn't crop or letterbox pages whose actual proportions differ.
private val PAGE_PLACEHOLDER_MIN_HEIGHT = 500.dp

// Caps vertical-mode page width on wide/tablet screens -- a phone's width never reaches
// this, so it's a no-op there. Multiplied by LocalKawabiScale.current.spacing at its
// usage site (grows to ~936.dp on EXPANDED) rather than routed through
// ResponsiveContainer's maxContentWidth -- a webtoon page's reading-width ergonomics
// differ from a settings list's, so it keeps its own base value.
private val READER_MAX_PAGE_WIDTH = 720.dp

// AsyncImage normally sizes its decode to the composable's measured constraints (here,
// screen width) to save memory -- Size.ORIGINAL overrides that. But Coil3 also caps
// *output* dimensions at 4096x4096 by default regardless of the requested size (its own
// OOM guard) -- some pages (AsuraScans webtoon strips, but also seen on other sources'
// tall/high-res pages) exceed that.
//
// Previously this raised the cap to 24000px tall and forced allowHardware(false)
// (software bitmaps), reasoning that software bitmaps aren't bound by the GPU's texture
// size limit the way hardware bitmaps are. That was wrong in practice: Compose's own
// render pipeline is hardware-accelerated regardless of the *decoded* bitmap's config --
// drawing a bitmap taller than the GPU's max texture size (commonly ~4096-8192px) still
// has to composite it through that pipeline, and a bitmap decoded far past that limit
// showed up as a black band or a visibly split/torn image instead of a decode crash.
// allowHardware(false) just moved the failure from decode-time to draw-time and made it
// silent. Capping the decode itself to a size that's safe to actually render, and letting
// Coil use hardware bitmaps (default, and far cheaper on RAM than software), fixes both
// the corruption and the memory pressure that came with always decoding in software.
//
// maxBitmapSize is set explicitly (matching, not exceeding, Coil's own 4096 default) so
// this stays intentional rather than silently inheriting whatever Coil's default happens
// to be. 8192 was tried first and still produced a black band splitting a page on a real
// device, meaning its actual GPU max texture size is below that -- 4096 is the
// widely-supported safe floor across real Android GPUs (Coil's own default exists for
// exactly this reason). Page dimensions ultimately come from wherever /image's `src` is
// pointed, and with the backend's catalog routes now login-optional, an attacker doesn't
// need an account to make this app decode whatever that param resolves to -- a cap here
// (rather than uncapped) also bounds that as an unbounded-allocation DoS.
private const val PAGE_MAX_BITMAP_WIDTH = 2048
private const val PAGE_MAX_BITMAP_HEIGHT = 4096

@Composable
private fun pageImageRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .size(Size.ORIGINAL)
        .maxBitmapSize(Size(PAGE_MAX_BITMAP_WIDTH, PAGE_MAX_BITMAP_HEIGHT))
        .build()
}

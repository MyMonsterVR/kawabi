package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.PageTitle
import com.mymonstervr.kawabi.app.common.ResponsiveContainer
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.common.roundIconButton
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

/**
 * The Anime tab: three panes (Home / Watching / Library) behind one top tab row, all
 * reading the same single library query out of [AnimeViewModel]. Home and Watching are
 * pull-to-refreshable (episode refresh + backend sync); Library isn't, since it's a view
 * over local rows only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: AnimeViewModel = koinViewModel(),
) {
    val tab by viewModel.tab.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullState = rememberPullToRefreshState()
    val scale = LocalKawabiScale.current

    val onContinue: (Long, String) -> Unit = { animeId, animeKey ->
        viewModel.resolveContinueEpisode(animeId) { episodeKey ->
            if (episodeKey != null) onEpisodeClick(episodeKey) else onAnimeClick(animeKey)
        }
    }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(NightSession.Background)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp * scale.spacing, end = 16.dp * scale.spacing, top = 18.dp * scale.spacing, bottom = 6.dp * scale.spacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PageTitle("Anime")
                    Box(modifier = Modifier.roundIconButton(onSearchClick), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search anime",
                            tint = NightSession.TextDim,
                            modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                        )
                    }
                }
                SegmentedTabs(
                    options = AnimeTab.entries.map { it.label },
                    selectedIndex = tab.ordinal,
                    onSelect = { viewModel.selectTab(AnimeTab.entries[it]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
                )
            }
        },
    ) { padding ->
        ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
                when (tab) {
                    AnimeTab.HOME -> PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AnimeHomePane(
                            entries = entries,
                            newEpisodes = viewModel.newEpisodes.collectAsState().value,
                            newReleases = viewModel.newReleases.collectAsState().value,
                            onContinue = onContinue,
                            onEpisodeClick = onEpisodeClick,
                            onAnimeClick = onAnimeClick,
                            onReleaseClick = { card -> viewModel.openKeyFor(card, onAnimeClick) },
                            onSeeAllWatching = { viewModel.selectTab(AnimeTab.WATCHING) },
                            onRetryNewEpisodes = viewModel::retryNewEpisodes,
                            onRetryNewReleases = viewModel::retryReleases,
                        )
                    }
                    AnimeTab.WATCHING -> PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AnimeWatchingPane(
                            entries = entries,
                            onContinue = onContinue,
                            onDetails = onAnimeClick,
                        )
                    }
                    AnimeTab.LIBRARY -> AnimeLibraryPane(
                        entries = entries,
                        gridColumns = viewModel.gridColumns.collectAsState().value,
                        sort = viewModel.sort.collectAsState().value,
                        statusFilter = viewModel.statusFilter.collectAsState().value,
                        query = viewModel.query.collectAsState().value,
                        onSortChange = viewModel::setSort,
                        onStatusFilterChange = viewModel::setStatusFilter,
                        onQueryChange = viewModel::onQueryChange,
                        onAnimeClick = onAnimeClick,
                    )
                }
            }
        }
    }
}

/** Section title shared by the Home rails -- small, bold, generous top space. */
@Composable
internal fun AnimeSectionHeader(title: String, modifier: Modifier = Modifier, loading: Boolean = false) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 10.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale.spacing),
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp * scale.font,
            fontWeight = FontWeight.Bold,
            color = NightSession.Text,
        )
        if (loading) {
            CircularProgressIndicator(
                color = NightSession.TextDim,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(16.dp * scale.spacing),
            )
        }
    }
}

@Composable
internal fun AnimeSectionNote(text: String) {
    val scale = LocalKawabiScale.current
    Text(
        text = text,
        fontSize = 11.sp * scale.font,
        color = NightSession.TextDim,
        modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
    )
}

@Composable
internal fun EmptyAnimeMessage(title: String, hint: String, modifier: Modifier = Modifier) {
    val scale = LocalKawabiScale.current
    Column(
        modifier = modifier.padding(32.dp * scale.spacing),
        verticalArrangement = Arrangement.spacedBy(6.dp * scale.spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, fontSize = 13.sp * scale.font, fontWeight = FontWeight.Bold, color = NightSession.Text)
        Text(text = hint, fontSize = 11.sp * scale.font, color = NightSession.TextDim, textAlign = TextAlign.Center)
    }
}

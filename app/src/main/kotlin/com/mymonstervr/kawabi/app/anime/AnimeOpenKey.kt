package com.mymonstervr.kawabi.app.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.usecase.AnimeIdentityMatcher
import kotlinx.coroutines.launch

/**
 * Which key a search/browse/new-release tap should open: the library row's own key when the
 * tapped card is the same show already in the library from another source, so the user lands
 * on their entry (watch state, tracker links, source switcher) instead of a second details
 * page for a duplicate (PLAN-anime.md section 18).
 */
internal fun ViewModel.resolveAnimeOpenKey(
    identityMatcher: AnimeIdentityMatcher,
    card: AnimeCardDto,
    onResolved: (String) -> Unit,
) {
    viewModelScope.launch {
        val match = identityMatcher.findFavorite(card.title, excludeKey = card.key)
        onResolved(match?.key ?: card.key)
    }
}

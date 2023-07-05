package net.primal.android.profile.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.primal.android.core.compose.feed.asFeedPostUi
import net.primal.android.feed.repository.FeedRepository
import net.primal.android.navigation.profileId
import net.primal.android.nostr.ext.displayNameUiFriendly
import net.primal.android.profile.details.ProfileContract.UiState
import net.primal.android.profile.repository.ProfileRepository
import net.primal.android.user.active.ActiveAccountStore
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    activeAccountStore: ActiveAccountStore,
    feedRepository: FeedRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val profileId: String =
        savedStateHandle.profileId ?: activeAccountStore.userAccount.value.pubkey

    private val _state = MutableStateFlow(
        UiState(
            profileId = profileId,
            authoredPosts = feedRepository.feedByDirective(feedDirective = "authored;$profileId")
                .map { it.map { feed -> feed.asFeedPostUi() } }
                .cachedIn(viewModelScope),
        )
    )
    val state = _state.asStateFlow()
    private fun setState(reducer: UiState.() -> UiState) {
        _state.getAndUpdate { it.reducer() }
    }

    init {
        fetchLatestProfile()
        observeProfile()
        observeProfileStats()
    }

    private fun fetchLatestProfile() = viewModelScope.launch {
        profileRepository.requestProfileUpdate(profileId = profileId)
    }

    private fun observeProfile() = viewModelScope.launch {
        profileRepository.observeProfile(profileId = profileId).collect {
            setState {
                copy(
                    profileDetails = ProfileDetailsUi(
                        pubkey = it.ownerId,
                        displayName = it.displayNameUiFriendly(),
                        coverUrl = it.banner,
                        avatarUrl = it.picture,
                        internetIdentifier = it.internetIdentifier,
                        about = it.about,
                        website = it.website,
                    )
                )
            }
        }
    }

    private fun observeProfileStats() = viewModelScope.launch {
        profileRepository.observeProfileStats(profileId = profileId).collect {
            setState {
                copy(
                    profileStats = ProfileStatsUi(
                        followingCount = it.following,
                        followersCount = it.followers,
                        notesCount = it.notes,
                    )
                )
            }
        }
    }

}

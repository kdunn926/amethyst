package com.vitorpamplona.amethyst.ui.screen

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrDataSource
import com.vitorpamplona.amethyst.service.NostrHiddenAccountsDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowersDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrUserProfileFollowsUserFeedViewModel(): UserFeedViewModel(
    NostrUserProfileFollowsDataSource
)

class NostrUserProfileFollowersUserFeedViewModel(): UserFeedViewModel(
    NostrUserProfileFollowersDataSource
)

class NostrHiddenAccountsFeedViewModel(): UserFeedViewModel(
    NostrHiddenAccountsDataSource
)

open class UserFeedViewModel(val dataSource: NostrDataSource<User>): ViewModel() {
    private val _feedContent = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    private fun refreshSuspended() {
        val notes = dataSource.loadTop()

        val oldNotesState = feedContent.value
        if (oldNotesState is UserFeedState.Loaded) {
            if (notes != oldNotesState.feed) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    fun updateFeed(notes: List<User>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = feedContent.value

            if (notes.isEmpty()) {
                _feedContent.update { UserFeedState.Empty }
            } else if (currentState is UserFeedState.Loaded) {
                // updates the current list
                currentState.feed.value = notes
            } else {
                _feedContent.update { UserFeedState.Loaded(mutableStateOf(notes)) }
            }
        }
    }

    var handlerWaiting = false
    fun invalidateData() {
        synchronized(handlerWaiting) {
            if (handlerWaiting) return

            handlerWaiting = true
            val scope = CoroutineScope(Job() + Dispatchers.Default)
            scope.launch {
                delay(100)
                refresh()
                handlerWaiting = false
            }
        }
    }

    private val cacheListener: (LocalCacheState) -> Unit = {
        invalidateData()
    }

    init {
        LocalCache.live.observeForever(cacheListener)
    }

    override fun onCleared() {
        LocalCache.live.removeObserver(cacheListener)

        dataSource.stop()
        viewModelScope.cancel()
        super.onCleared()
    }
}
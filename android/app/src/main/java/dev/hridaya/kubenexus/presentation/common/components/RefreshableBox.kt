package dev.hridaya.kubenexus.presentation.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Pull-to-refresh with the M3 Expressive contained loading indicator, which stays on screen
 * until the refresh finishes. Screens that already show refresh progress elsewhere, such as in
 * a header, pass [showIndicatorWhileRefreshing] = false so only one indicator is visible.
 *
 * Pass a collapsing app bar's [appBarScrollConnection] here rather than on the Scaffold, so a
 * downward drag expands the app bar before it starts a refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    showIndicatorWhileRefreshing: Boolean = true,
    appBarScrollConnection: NestedScrollConnection? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (showIndicatorWhileRefreshing || !isRefreshing) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        Box(
            modifier = if (appBarScrollConnection != null) {
                Modifier.fillMaxSize().nestedScroll(appBarScrollConnection)
            } else {
                Modifier.fillMaxSize()
            },
            content = content,
        )
    }
}

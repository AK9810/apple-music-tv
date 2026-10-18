package com.applemusicktv.ui.navigation

import androidx.annotation.StringRes
import com.applemusicktv.R

/**
 * Top navigation tabs. Labels are resource ids, not strings: the tab bar is read in one place
 * ([com.applemusicktv.ui.components.TopNavBar]) so it follows the app language automatically.
 */
enum class TopNavTab(@StringRes val labelRes: Int) {
    ListenNow(R.string.nav_listen_now),
    Browse(R.string.nav_browse),
    Library(R.string.nav_library),
    Search(R.string.nav_search),
    NowPlaying(R.string.nav_now_playing),
    Dev(R.string.nav_settings),
}

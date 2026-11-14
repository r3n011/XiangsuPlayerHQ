package com.theveloper.pixelplay.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Resolve formerly hardcoded labels in the selected language, with Chinese as the fallback. */
@Composable
fun localizedStringResource(@StringRes resourceId: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedContext = remember(context, configuration) {
        @Suppress("DEPRECATION")
        AppLocaleManager.contextForLocale(context, configuration.locale, configuration)
    }
    return if (formatArgs.isEmpty()) localizedContext.getString(resourceId)
    else localizedContext.getString(resourceId, *formatArgs)
}

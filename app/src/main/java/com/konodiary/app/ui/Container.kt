package com.konodiary.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.konodiary.app.AppContainer
import com.konodiary.app.KonoApp

/** Convenience accessor for the app's [AppContainer] from within composables. */
@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { context.konoContainer() }
}

fun Context.konoContainer(): AppContainer =
    (applicationContext as KonoApp).container

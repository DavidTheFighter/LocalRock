package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable

/**
 * Returns an action that asks the system for permission to show notifications and reports the
 * answer. Where no permission is needed, or it was already given, it reports true straight away.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

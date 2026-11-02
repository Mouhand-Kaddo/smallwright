@file:JvmName("ControllerHost")

package dev.smallwright.controller.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Puts the terminal screen on an activity and keeps autofill away from it, so the builder key is
 * never offered to or captured by an autofill service. The controller calls this once.
 */
fun show(activity: ComponentActivity, ui: UiState) {
    activity.setContent { ControllerScreen(ui) }
    activity.findViewById<View>(android.R.id.content).importantForAutofill =
        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
}

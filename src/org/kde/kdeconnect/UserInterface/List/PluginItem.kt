package org.kde.kdeconnect.UserInterface.List

import android.content.Context
import android.graphics.drawable.Drawable
import org.kde.kdeconnect.Plugins.Plugin

class PluginItem(
    val context: Context,
    val header: String,
    val textStyleRes: Int? = null,
) {

    var action: (() -> Unit)? = null
    var icon: Drawable? = null

    constructor(
        context: Context,
        plugin: Plugin,
        action: (Plugin) -> Unit,
        textStyleRes: Int? = null,
    ) : this(
        context = context,
        header = plugin.displayName,
        textStyleRes = textStyleRes,
    ) {
        this.action = { action(plugin) }
        this.icon = plugin.icon
    }
}
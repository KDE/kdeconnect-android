package org.kde.kdeconnect.UserInterface.List

import android.annotation.TargetApi
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.StubTextPlugin
import org.kde.kdeconnect_tp.R

/**
 * Adapter for showing enabled plugins and permission requests
 * can be used with following layouts:
 * list_plugin_entry - card view with text and icon
 * list_item_plugin_header - plain TextView
 * Any other TextView layout
 */
class PluginAdapter(
    private val pluginList: ArrayList<Pair<Plugin, (() -> Unit)?>>,
    private val layout: Int,
) : RecyclerView.Adapter<PluginAdapter.PluginViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, type: Int) =
        PluginViewHolder(LayoutInflater.from(viewGroup.context).inflate(layout, viewGroup, false))

    override fun getItemCount() = pluginList.size

    @TargetApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: PluginViewHolder, position: Int) {
        pluginList[position].let { (plugin, action) ->
            holder.pluginTitle.text = plugin.displayName
            holder.pluginIcon?.setImageDrawable(plugin.icon)

            //Set regular text for unclickable StubTextPlugin and bold for supposedly clickable TextView items
            when {
                plugin is StubTextPlugin ->
                    holder.pluginTitle.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium)
                holder.itemView is TextView ->
                    holder.pluginTitle.setTextAppearance(R.style.TextAppearance_Material3_LabelLarge)
            }

            action?.let { holder.itemView.setOnClickListener { action.invoke() } }
        }
    }

    class PluginViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pluginTitle: TextView = view.findViewById(R.id.list_item_entry_title) ?: view as TextView
        val pluginIcon: ImageView? = view.findViewById(R.id.list_item_entry_icon)
    }

}
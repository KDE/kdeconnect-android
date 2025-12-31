package org.kde.kdeconnect.Plugins.RunCommandPlugin

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.kde.kdeconnect_tp.R
class CommandEntryAdapter(
    private val commands: List<CommandEntry>,
    private val onItemClick: (CommandEntry) -> kotlin.Unit
) : RecyclerView.Adapter<CommandEntryAdapter.CommandViewHolder>() {

    class CommandViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commandName: TextView = view.findViewById(R.id.command_name)
        
        init {
            // 使卡片保持正方形
            view.post {
                val width = view.width
                val params = view.layoutParams
                params.height = width
                view.layoutParams = params
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_command_button, parent, false)
        return CommandViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommandViewHolder, position: Int) {
        try {
            val command = commands[position]
            holder.commandName.text = command.getName()
            
            holder.itemView.setOnClickListener {
                onItemClick.invoke(command)
            }
        } catch (e: Exception) {
            Log.e("CommandEntryAdapter", "绑定命令时出错", e)
        }
    }

    override fun getItemCount() = commands.size
}
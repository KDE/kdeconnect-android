package org.kde.kdeconnect.plugins.systemvolume

import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.kde.kdeconnect_tp.R.drawable
import org.kde.kdeconnect_tp.databinding.ListItemSystemvolumeBinding

class SinkItemHolder(
    val viewBinding: ListItemSystemvolumeBinding,
    val plugin: SystemVolumePlugin,
    val onTrackingChanged: (Boolean) -> Unit
) : RecyclerView.ViewHolder(viewBinding.root) {

    private fun getOnVolumeSeekChanged(sink: Sink) = object : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar, i: Int, triggeredByUser: Boolean) {
            if (triggeredByUser) {
                plugin.sendVolume(sink.name, seekBar.progress)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            onTrackingChanged(true)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            onTrackingChanged(false)
            plugin.sendVolume(sink.name, seekBar.progress)
        }
    }

    private fun getOnLongClickAction(sink: Sink) = View.OnLongClickListener { v ->
        Toast.makeText(v.context, sink.name, Toast.LENGTH_SHORT).show()
        true
    }

    fun bind(sink: Sink) {
        viewBinding.apply {
            //Card
            sinkCard.setOnLongClickListener(getOnLongClickAction(sink))
            //Mute
            systemvolumeMute.setOnClickListener { plugin.sendMute(sink.name, !sink.mute) }
            //Volume seek
            systemvolumeSeek.setOnSeekBarChangeListener(getOnVolumeSeekChanged(sink))
            systemvolumeSeek.max = sink.maxVolume
            systemvolumeSeek.progress = sink.volume
            //Radio button
            systemvolumeLabel.setOnLongClickListener(getOnLongClickAction(sink))
            systemvolumeLabel.setOnCheckedChangeListener { _, isChecked -> if (isChecked) plugin.sendEnable(sink.name) }
            systemvolumeLabel.isChecked = sink.isDefault
            systemvolumeLabel.text = sink.description
            //Icon
            val iconRes = if (sink.mute) drawable.ic_volume_mute else drawable.ic_volume
            systemvolumeMute.setImageResource(iconRes)
        }
    }

}

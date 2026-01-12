package org.kde.kdeconnect.Plugins.SystemVolumePlugin

import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.kde.kdeconnect_tp.R.drawable
import org.kde.kdeconnect_tp.databinding.ListItemSystemvolumeBinding

class SinkItemHolder(
    val viewBinding: ListItemSystemvolumeBinding,
    val plugin: SystemVolumePlugin,
    val onTrackingChanged: (Boolean) -> Unit
) : RecyclerView.ViewHolder(viewBinding.root),
    View.OnClickListener,
    CompoundButton.OnCheckedChangeListener,
    SeekBar.OnSeekBarChangeListener,
    View.OnLongClickListener {

    private var sink: Sink? = null

    init {
        viewBinding.sinkCard.setOnLongClickListener(this)
        viewBinding.systemvolumeLabel.setOnLongClickListener(this)
        viewBinding.systemvolumeLabel.setOnCheckedChangeListener(this)
        viewBinding.systemvolumeMute.setOnClickListener(this)
        viewBinding.systemvolumeSeek.setOnSeekBarChangeListener(this)
    }

    override fun onProgressChanged(seekBar: SeekBar, i: Int, triggeredByUser: Boolean) {
        if (triggeredByUser) {
            sink?.let { plugin.sendVolume(it.name, seekBar.progress) }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        onTrackingChanged(true)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        onTrackingChanged(false)
        sink?.let { plugin.sendVolume(it.name, seekBar.progress) }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (isChecked) {
            sink?.let { plugin.sendEnable(it.name) }
        }
    }

    override fun onLongClick(v: View): Boolean {
        sink?.let { Toast.makeText(v.context, it.name, Toast.LENGTH_SHORT).show() }
        return true
    }

    override fun onClick(view: View) {
        sink?.let { plugin.sendMute(it.name, !it.mute) }
    }

    fun bind(sink: Sink) {
        this.sink = sink
        viewBinding.apply {
            //Volume seek
            systemvolumeSeek.max = sink.maxVolume
            systemvolumeSeek.progress = sink.volume
            //Radio button
            systemvolumeLabel.isChecked = sink.isDefault
            systemvolumeLabel.text = sink.description
            //Icon
            val iconRes = if (sink.mute) drawable.ic_volume_mute else drawable.ic_volume
            systemvolumeMute.setImageResource(iconRes)
        }
    }

}
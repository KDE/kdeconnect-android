package org.kde.kdeconnect.Plugins.RunCommandPlugin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.kde.kdeconnect_tp.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class CommandEntryAdapter(
    private val commands: MutableList<CommandEntry>,
    private val onItemClick: (CommandEntry) -> kotlin.Unit
) : RecyclerView.Adapter<CommandEntryAdapter.CommandViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())

    class CommandViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commandName: TextView = view.findViewById(R.id.command_name)
        val commandBadge: TextView = view.findViewById(R.id.command_badge)
        val commandFavicon: ImageView = view.findViewById(R.id.command_favicon)
        
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

            val name = command.getName()?.trim().orEmpty()
            val badge = if (name.isNotEmpty()) name.substring(0, 1).uppercase() else "#"
            holder.commandBadge.text = badge

            bindFavicon(holder, command)
            
            holder.itemView.setOnClickListener {
                onItemClick.invoke(command)
            }
        } catch (e: Exception) {
            Log.e("CommandEntryAdapter", "绑定命令时出错", e)
        }
    }

    private fun bindFavicon(holder: CommandViewHolder, command: CommandEntry) {
        val raw = command.getCommand()?.trim().orEmpty()
        val host = extractHostOrDomain(raw)
        if (host.isNullOrEmpty()) {
            holder.commandFavicon.setImageDrawable(null)
            holder.commandFavicon.visibility = View.GONE
            holder.commandBadge.visibility = View.VISIBLE
            holder.commandFavicon.tag = null
            return
        }

        val faviconUrl = buildFaviconUrl(host)
        holder.commandFavicon.tag = faviconUrl

        val cached = faviconCache.get(faviconUrl)
        if (cached != null) {
            holder.commandFavicon.setImageBitmap(cached)
            holder.commandFavicon.visibility = View.VISIBLE
            holder.commandBadge.visibility = View.GONE
            return
        }

        holder.commandFavicon.setImageDrawable(null)
        holder.commandFavicon.visibility = View.GONE
        holder.commandBadge.visibility = View.VISIBLE

        executor.execute {
            val context = holder.itemView.context.applicationContext
            val bmp = loadFaviconFromDisk(context.cacheDir, host) ?: fetchBitmap(faviconUrl)?.also {
                saveFaviconToDisk(context.cacheDir, host, it)
            }
            if (bmp != null) {
                faviconCache.put(faviconUrl, bmp)
            }
            mainHandler.post {
                val tag = holder.commandFavicon.tag as? String
                if (tag != faviconUrl) return@post
                if (bmp != null) {
                    holder.commandFavicon.setImageBitmap(bmp)
                    holder.commandFavicon.visibility = View.VISIBLE
                    holder.commandBadge.visibility = View.GONE
                } else {
                    holder.commandFavicon.visibility = View.GONE
                    holder.commandBadge.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadFaviconFromDisk(cacheDir: File, host: String): Bitmap? {
        return try {
            val file = faviconFile(cacheDir, host)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveFaviconToDisk(cacheDir: File, host: String, bitmap: Bitmap) {
        try {
            val file = faviconFile(cacheDir, host)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {
        }
    }

    private fun faviconFile(cacheDir: File, host: String): File {
        val safe = host.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        val dir = File(cacheDir, "kdeconnect_favicons")
        return File(dir, "$safe.png")
    }

    private fun extractFirstHttpUrl(text: String): String? {
        val match = URL_REGEX.find(text) ?: return null
        return match.value.trimEnd(',', '.', ';', ')', ']', '}')
    }

    private fun extractHostOrDomain(text: String): String? {
        val url = extractFirstHttpUrl(text)
        val hostFromUrl = url?.let { extractHost(it) }
        if (!hostFromUrl.isNullOrBlank()) return hostFromUrl

        val domainMatch = DOMAIN_REGEX.find(text) ?: return null
        val token = domainMatch.value
        val cleaned = token
            .trim()
            .trimEnd(',', '.', ';', ')', ']', '}')
            .removePrefix("http://")
            .removePrefix("https://")
        val hostOnly = cleaned.substringBefore('/')
        return hostOnly.removePrefix("www.")
    }

    private fun extractHost(url: String): String? {
        return try {
            val rawHost = URI(url).host ?: URL(url).host
            rawHost?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFaviconUrl(host: String): String {
        val encoded = try {
            URLEncoder.encode(host, "UTF-8")
        } catch (_: Exception) {
            host
        }
        return "https://www.google.com/s2/favicons?sz=64&domain=$encoded"
    }

    private fun fetchBitmap(urlString: String): Bitmap? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection)
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = true
            conn.doInput = true
            conn.connect()

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            conn.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }.also {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun getItemCount() = commands.size

    fun setCommands(newCommands: List<CommandEntry>) {
        commands.clear()
        commands.addAll(newCommands)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val item = commands.removeAt(fromPosition)
        commands.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCommandKeys(): List<String> {
        return commands.map { it.key }
    }

    fun getCommandAt(position: Int): CommandEntry? {
        if (position < 0 || position >= commands.size) return null
        return commands[position]
    }

    companion object {
        private val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

        private val DOMAIN_REGEX = Regex(
            "(?i)(?:^|\\s)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+)(?::\\d+)?(?:/[^\\s]*)?"
        )

        private val executor = Executors.newFixedThreadPool(2)

        private val faviconCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(60) {
            override fun sizeOf(key: String, value: Bitmap): Int = 1
        }
    }
}
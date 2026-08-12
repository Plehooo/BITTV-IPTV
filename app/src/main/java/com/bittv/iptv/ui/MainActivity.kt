package com.bittv.iptv.ui

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.data.Channel
import com.bittv.iptv.data.M3uParser
import com.bittv.iptv.util.HeaderParser
import com.bittv.iptv.util.PlaylistRepository
import org.json.JSONObject
import java.util.Locale

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var playlistUrl: EditText
    private lateinit var headerInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var groupSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var activeChannelText: TextView
    private lateinit var adapter: ChannelAdapter
    private lateinit var repository: PlaylistRepository

    private val allChannels = mutableListOf<Channel>()
    private val favorites = linkedSetOf<String>()
    private val history = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var activeChannel: Channel? = null
    private var lastFailedChannel: Channel? = null
    private var automaticRetries = 0
    private var currentFilter = "All"
    private var suppressGroupCallback = false
    private var playerHeaders: Map<String, String> = emptyMap()
    private var defaultHeaders: Map<String, String> = emptyMap()

    private val prefs by lazy { getSharedPreferences("bittv", MODE_PRIVATE) }

    private val openPlaylistFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) loadPlaylistFromFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        repository = PlaylistRepository(this)
        restoreState()
        bindViews()
        setupPlayer()
        setupList()
        setupControls()
        loadConfigJson()
    }

    private fun loadConfigJson() {
        repository.loadFromUri("file:///android_asset/config.json", 
            onSuccess = { content ->
                try {
                    val json = JSONObject(content)
                    val headers = json.optJSONObject("defaultHeaders")?.let { obj ->
                        mutableMapOf<String, String>().apply {
                            obj.keys().forEach { key ->
                                put(key, obj.getString(key))
                            }
                        }
                    } ?: mutableMapOf()
                    defaultHeaders = headers
                    
                    val channels = json.optJSONArray("channels")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val ch = arr.getJSONObject(i)
                            Channel(
                                id = ch.getString("id"),
                                name = ch.getString("name"),
                                logoUrl = ch.optString("logo").takeIf { it.isNotEmpty() },
                                group = ch.getString("category"),
                                streamUrl = ch.getString("url"),
                                headers = defaultHeaders
                            )
                        }
                    } ?: emptyList()
                    
                    allChannels.clear()
                    allChannels.addAll(channels)
                    renderGroups()
                    applyFilter()
                    statusText.text = "Loaded ${channels.size} channels"
                } catch (e: Exception) {
                    statusText.text = "Config error: ${e.message}"
                }
            },
            onError = { statusText.text = "Error loading config: ${it.message}" }
        )
    }

    private fun bindViews() {
        playlistUrl = findViewById(R.id.playlistUrl)
        headerInput = findViewById(R.id.headerInput)
        searchInput = findViewById(R.id.searchInput)
        groupSpinner = findViewById(R.id.groupSpinner)
        statusText = findViewById(R.id.statusText)
        retryButton = findViewById(R.id.retryButton)
        playerView = findViewById(R.id.playerView)
        playerContainer = findViewById(R.id.playerContainer)
        activeChannelText = findViewById(R.id.activeChannelText)
        playlistUrl.setText(prefs.getString("playlistUrl", ""))
        headerInput.setText(prefs.getString("headers", ""))
    }

    private fun setupPlayer() {
        player = buildPlayer()
        playerView.player = player
        attachPlayerListener()
    }

    private fun attachPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastFailedChannel = activeChannel
                automaticRetries++
                statusText.text = formatPlaybackError(error)
                retryButton.visibility = View.VISIBLE
                if (automaticRetries <= 3) {
                    val delay = automaticRetries * 1500L
                    mainHandler.postDelayed({
                        if (!isFinishing && lastFailedChannel != null) playChannel(lastFailedChannel!!, true)
                    }, delay)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    statusText.text = "Buffering: ${activeChannel?.name ?: ""}"
                } else if (playbackState == Player.STATE_READY) {
                    statusText.text = "Playing: ${activeChannel?.name ?: ""}"
                    retryButton.visibility = View.GONE
                }
            }
        })
    }

    private fun buildPlayer(): ExoPlayer {
        val headers = defaultHeaders + HeaderParser.parse(headerInput.text.toString())
        playerHeaders = headers
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    private fun setupList() {
        adapter = ChannelAdapter(
            onChannelClick = { playChannel(it, false) },
            onFavoriteClick = { toggleFavorite(it) },
            isFavorite = { favorites.contains(it.streamUrl) }
        )
        findViewById<RecyclerView>(R.id.channelList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupControls() {
        findViewById<Button>(R.id.loadPlaylistButton).setOnClickListener { loadPlaylistFromUrl() }
        findViewById<Button>(R.id.playUrlButton).setOnClickListener { playDirectUrl() }
        findViewById<Button>(R.id.fileButton).setOnClickListener { openPlaylistFile.launch(arrayOf("text/*", "application/octet-stream", "application/x-mpegurl", "application/vnd.apple.mpegurl")) }
        findViewById<Button>(R.id.headerHelpButton).setOnClickListener {
            Toast.makeText(this, "One header per line, e.g. User-Agent: Mozilla/5.0", Toast.LENGTH_LONG).show()
        }
        retryButton.setOnClickListener { activeChannel?.let { playChannel(it, true) } }
        searchInput.addTextChangedListener(SimpleTextWatcher { applyFilter() })
        groupSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressGroupCallback) {
                    currentFilter = parent?.getItemAtPosition(position)?.toString() ?: "All"
                    applyFilter()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.previousButton).setOnClickListener { playAdjacent(-1) }
        findViewById<Button>(R.id.nextButton).setOnClickListener { playAdjacent(1) }
        findViewById<Button>(R.id.fullscreenButton).setOnClickListener { toggleFullscreen() }
    }

    private fun playDirectUrl() {
        val url = playlistUrl.text.toString().trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            statusText.text = "Enter a valid HTTP/HTTPS stream URL."
            return
        }
        val channel = Channel(
            id = "direct-url",
            name = "Direct stream",
            logoUrl = null,
            group = "Direct",
            streamUrl = url,
            headers = defaultHeaders + HeaderParser.parse(headerInput.text.toString())
        )
        allChannels.removeAll { it.streamUrl == channel.streamUrl }
        allChannels.add(0, channel)
        renderGroups()
        applyFilter()
        playChannel(channel, false)
    }

    private fun loadPlaylistFromUrl() {
        val url = playlistUrl.text.toString().trim()
        if (url.isBlank()) {
            statusText.text = "Playlist URL is empty"
            return
        }
        prefs.edit().putString("playlistUrl", url).apply()
        val headers = defaultHeaders + HeaderParser.parse(headerInput.text.toString())
        prefs.edit().putString("headers", headerInput.text.toString()).apply()
        statusText.text = "Loading..."
        repository.load(url, headers,
            onSuccess = { content ->
                val parsed = M3uParser.parse(content, url, headers)
                allChannels.clear()
                allChannels.addAll(parsed)
                renderGroups()
                applyFilter()
                statusText.text = "Loaded ${parsed.size} channels from M3U"
            },
            onError = { e ->
                statusText.text = "Error: ${e.message}"
            }
        )
    }

    private fun loadPlaylistFromFile(uri: Uri) {
        statusText.text = "Loading from file..."
        val headers = defaultHeaders + HeaderParser.parse(headerInput.text.toString())
        repository.loadFromUri(uri.toString(),
            onSuccess = { content ->
                val parsed = M3uParser.parse(content, null, headers)
                allChannels.clear()
                allChannels.addAll(parsed)
                renderGroups()
                applyFilter()
                statusText.text = "Loaded ${parsed.size} channels from file"
            },
            onError = { e ->
                statusText.text = "Error: ${e.message}"
            }
        )
    }

    private fun renderGroups() {
        val groups = listOf("All") + allChannels.map { it.group }.toSet().sorted()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        suppressGroupCallback = true
        groupSpinner.adapter = adapter
        suppressGroupCallback = false
    }

    private fun applyFilter() {
        val search = searchInput.text.toString().lowercase(Locale.getDefault())
        val filtered = allChannels.filter { channel ->
            (currentFilter == "All" || channel.group == currentFilter) &&
            (search.isEmpty() || channel.name.lowercase(Locale.getDefault()).contains(search))
        }
        adapter.submitList(filtered)
    }

    private fun playChannel(channel: Channel, isRetry: Boolean) {
        activeChannel = channel
        activeChannelText.text = channel.name
        playerContainer.visibility = View.VISIBLE
        history.removeAll { it == channel.streamUrl }
        history.addFirst(channel.streamUrl)
        if (history.size > 20) history.removeLast()
        loadStreamWithHeaders(channel)
    }

    private fun loadStreamWithHeaders(channel: Channel) {
        automaticRetries = 0
        statusText.text = "Loading: ${channel.name}"
        val mediaItem = MediaItem.fromUri(channel.streamUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    private fun playAdjacent(offset: Int) {
        val current = activeChannel ?: return
        val items = adapter.currentItems()
        val idx = items.indexOfFirst { it.streamUrl == current.streamUrl }
        if (idx >= 0) {
            val next = items.getOrNull(idx + offset)
            if (next != null) playChannel(next, false)
        }
    }

    private fun toggleFullscreen() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun toggleFavorite(channel: Channel) {
        if (favorites.contains(channel.streamUrl)) {
            favorites.remove(channel.streamUrl)
        } else {
            favorites.add(channel.streamUrl)
        }
        adapter.notifyDataSetChanged()
    }

    private fun restoreState() {
        prefs.getStringSet("favorites", emptySet())?.forEach { favorites.add(it) }
    }

    private fun formatPlaybackError(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network error"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File not found"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Bad manifest"
            else -> "Playback error: ${error.message}"
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}

class SimpleTextWatcher(val onChange: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: android.text.Editable?) = onChange()
}
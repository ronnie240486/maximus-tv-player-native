package com.maximus.tvplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private data class ChannelEditorial(
    val eyebrow: String,
    val description: String,
    val tags: String,
    val currentProgram: String,
    val currentDescription: String,
    val time: String,
    val nextProgram: String,
)

class MainActivity : Activity() {
    private enum class PreviewMode { NONE, TRAILER, CONTENT }

    private val pageSize = 120
    private lateinit var channelList: RecyclerView
    private lateinit var videoPreview: FrameLayout
    private lateinit var categoryList: LinearLayout
    private lateinit var navItems: LinearLayout
    private lateinit var appLogo: ImageView
    private lateinit var remoteBackground: ImageView
    private lateinit var brandMark: TextView
    private lateinit var brandSubtitle: TextView
    private lateinit var searchHint: TextView
    private lateinit var greeting: TextView
    private lateinit var channelHeading: TextView
    private lateinit var videoPreviewText: TextView
    private lateinit var previewLogo: ImageView
    private lateinit var heroImage: ImageView
    private lateinit var liveBadge: TextView
    private lateinit var detailEyebrow: TextView
    private lateinit var detailChannelName: TextView
    private lateinit var detailTags: TextView
    private lateinit var detailDescription: TextView
    private lateinit var aboutLabel: TextView
    private lateinit var nowLabel: TextView
    private lateinit var currentProgram: TextView
    private lateinit var currentProgramDescription: TextView
    private lateinit var programTime: TextView
    private lateinit var nextProgram: TextView
    private lateinit var actionRow: LinearLayout
    private lateinit var vodCards: LinearLayout
    private lateinit var vodTitle: TextView
    private lateinit var homePanel: ScrollView
    private lateinit var homeHeroImage: ImageView
    private lateinit var homeHeroTitle: TextView
    private lateinit var homeHeroDescription: TextView
    private lateinit var homeMoviesCard: FrameLayout
    private lateinit var homeSeriesCard: FrameLayout
    private lateinit var homeCartoonsCard: FrameLayout
    private var homeMode = false
    private var miniPlayer: ExoPlayer? = null
    private var miniPlayerView: PlayerView? = null
    private var miniPlayerEntryKey: String? = null
    private var miniPlayerDialog: Dialog? = null
    private var miniTrailerView: WebView? = null
    private var previewMode = PreviewMode.NONE
    private var seriesSeasonsDialog: Dialog? = null
    private var seriesEpisodesDialog: Dialog? = null

    private val repository by lazy { PlaylistRepository(this) }
    private val appIntegration = AppIntegrationRepository()
    private val imageLoader = ImageLoader()
    private val epgRepository = EpgRepository()
    private var epgByChannel: Map<String, List<EpgProgram>> = emptyMap()
    private lateinit var catalogAdapter: CatalogAdapter
    private var catalog = CatalogSnapshot(emptyList())
    private var databaseBackedCatalog = false
    private val pagedItems = ArrayList<CatalogEntry>()
    private var pageLoading = false
    private var pageFinished = false
    private var pageRequestId = 0
    private var selectedEntry: CatalogEntry? = null
    private var selectedCategory = "Todos"
    private var query = ""
    private var favoritesOnly = false
    private var currentKind = MediaKind.LIVE
    private var sortAlphabetically = false
    private var remoteBannerUrl = ""
    private var remoteEpgUrl = ""

    private val editorials = mapOf(
        "animal planet" to ChannelEditorial(
            "Natureza e vida selvagem",
            "Documentários, expedições e histórias sobre animais, seus habitats e a relação entre as pessoas e o mundo natural.",
            "Animais   •   Natureza   •   Documentários",
            "Explorando a Selva",
            "Uma expedição acompanha espécies e paisagens selvagens em diferentes regiões do planeta.",
            "12:51 – 13:42",
            "A seguir  •  Predadores do Mundo  •  13:42",
        ),
        "cartoon network" to ChannelEditorial(
            "Desenhos e diversão",
            "Animações, aventuras e personagens para a família acompanhar ao longo do dia.",
            "Infantil   •   Animação   •   Família",
            "Aventuras no Mundo Colorido",
            "Uma turma de amigos descobre novas histórias em um universo cheio de imaginação.",
            "12:40 – 13:10",
            "A seguir  •  O Mundo de Greg  •  13:10",
        ),
        "discovery" to ChannelEditorial(
            "Ciência, aventura e descoberta",
            "Séries e documentários que exploram ciência, tecnologia, engenharia, aventura e os mistérios do mundo.",
            "Ciência   •   Aventura   •   Documentários",
            "Mestres da Engenharia",
            "Projetos impressionantes e as pessoas que transformam grandes ideias em realidade.",
            "12:30 – 13:30",
            "A seguir  •  Sobrevivência Extrema  •  13:30",
        ),
        "national geographic" to ChannelEditorial(
            "Conhecimento e exploração",
            "Produções sobre ciência, história, cultura, viagens e vida selvagem com imagens de diferentes lugares do planeta.",
            "Ciência   •   Viagens   •   Natureza",
            "Segredos do Oceano",
            "Uma jornada pelas profundezas do mar revela comportamentos e ambientes ainda pouco conhecidos.",
            "12:10 – 13:20",
            "A seguir  •  Grandes Civilizações  •  13:20",
        ),
        "espn" to ChannelEditorial(
            "Esportes e competição",
            "Eventos esportivos ao vivo, programas de debate, notícias e análises para acompanhar os principais campeonatos.",
            "Esportes   •   Ao vivo   •   Análises",
            "ESPN na Área",
            "Notícias, comentários e os principais destaques esportivos do dia.",
            "12:00 – 13:00",
            "A seguir  •  Linha de Passe  •  13:00",
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        setContentView(R.layout.activity_main)
        sortAlphabetically = getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_SORT_ALPHA, false)
        bindViews()
        setupCatalogList()
        renderNavigation()
        renderCategories()
        renderCatalog()
        selectedEntry = catalog.entries.firstOrNull()
        selectedEntry?.let { selectEntry(it, false) }
        loadRemoteConfiguration()
    }

    private fun bindViews() {
        appLogo = findViewById(R.id.appLogo)
        remoteBackground = findViewById(R.id.remoteBackground)
        brandMark = findViewById(R.id.brandMark)
        brandSubtitle = findViewById(R.id.brandSubtitle)
        channelList = findViewById(R.id.channelList)
        videoPreview = findViewById(R.id.videoPreview)
        categoryList = findViewById(R.id.categoryList)
        navItems = findViewById(R.id.navItems)
        searchHint = findViewById(R.id.searchHint)
        greeting = findViewById(R.id.greeting)
        channelHeading = findViewById(R.id.channelHeading)
        videoPreviewText = findViewById(R.id.videoPreviewText)
        previewLogo = findViewById(R.id.previewLogo)
        heroImage = findViewById(R.id.heroImage)
        liveBadge = findViewById(R.id.liveBadge)
        detailEyebrow = findViewById(R.id.detailEyebrow)
        detailChannelName = findViewById(R.id.detailChannelName)
        detailTags = findViewById(R.id.detailTags)
        detailDescription = findViewById(R.id.detailDescription)
        aboutLabel = findViewById(R.id.aboutLabel)
        nowLabel = findViewById(R.id.nowLabel)
        currentProgram = findViewById(R.id.currentProgram)
        currentProgramDescription = findViewById(R.id.currentProgramDescription)
        programTime = findViewById(R.id.programTime)
        nextProgram = findViewById(R.id.nextProgram)
        actionRow = findViewById(R.id.actionRow)
        vodCards = findViewById(R.id.vodCards)
        vodTitle = findViewById(R.id.vodTitle)
        homePanel = findViewById(R.id.homePanel)
        homeHeroImage = findViewById(R.id.homeHeroImage)
        homeHeroTitle = findViewById(R.id.homeHeroTitle)
        homeHeroDescription = findViewById(R.id.homeHeroDescription)
        homeMoviesCard = findViewById(R.id.homeMoviesCard)
        homeSeriesCard = findViewById(R.id.homeSeriesCard)
        homeCartoonsCard = findViewById(R.id.homeCartoonsCard)
        homeMoviesCard.setOnClickListener { switchSection(MediaKind.MOVIE) }
        homeSeriesCard.setOnClickListener { switchSection(MediaKind.SERIES) }
        homeCartoonsCard.setOnClickListener { switchSection(MediaKind.MOVIE) }
        findViewById<View>(R.id.homeNavHome).setOnClickListener { showHome() }
        findViewById<View>(R.id.homeNavChannels).setOnClickListener { switchSection(MediaKind.LIVE) }
        findViewById<View>(R.id.homeNavMovies).setOnClickListener { switchSection(MediaKind.MOVIE) }
        findViewById<View>(R.id.homeNavSeries).setOnClickListener { switchSection(MediaKind.SERIES) }
        searchHint.setOnClickListener { showSearchDialog() }
        videoPreview.isFocusable = true
        videoPreview.isClickable = true
        videoPreview.setOnClickListener { selectedEntry?.let { handleEntryClick(it) } }
    }

    private fun setupCatalogList() {
        catalogAdapter = CatalogAdapter(
            imageLoader = imageLoader,
            fallbackLogo = ::fallbackLogo,
            onSelected = { selectEntry(it, false) },
            onClicked = { handleEntryClick(it) },
        )
        channelList.layoutManager = LinearLayoutManager(this)
        channelList.adapter = catalogAdapter
        channelList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= catalogAdapter.itemCount - 12) loadNextPage()
            }
        })
    }

    private fun renderNavigation() {
        navItems.removeAllViews()
        val items = listOf(
            Triple("INÍCIO", R.drawable.nav_home_3d, "Início"),
            Triple("CANAIS", R.drawable.nav_live_3d, "Canais"),
            Triple("FILMES", R.drawable.nav_movies_3d, "Filmes"),
            Triple("SÉRIES", R.drawable.nav_series_3d, "Séries"),
            Triple("FAVORITOS", R.drawable.nav_favorites_3d, "Favoritos"),
            Triple("AJUSTES", R.drawable.nav_settings_3d, "Ajustes"),
        )
        items.forEachIndexed { index, (label, iconRes, captionText) ->
            lateinit var icon: ImageView
            lateinit var caption: TextView
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                tag = label
                clipChildren = false
                clipToPadding = false
                setPadding(0, 6, 0, 6)
                layoutParams = LinearLayout.LayoutParams(-1, 220).apply { setMargins(6, 10, 6, 10) }
                setOnClickListener {
                    when (label) {
                        "INÍCIO" -> showHome()
                        "CANAIS" -> switchSection(MediaKind.LIVE)
                        "FILMES" -> switchSection(MediaKind.MOVIE)
                        "SÉRIES" -> switchSection(MediaKind.SERIES)
                        "FAVORITOS" -> switchFavorites()
                        "AJUSTES" -> showSettingsDialog()
                    }
                }
                setOnFocusChangeListener { view, hasFocus ->
                    updateNavigationVisuals(if (hasFocus) view else navItems.findFocus())
                }
            }
            icon = ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = if (isNavigationSelected(label)) 1f else 0.72f
                background = null
                layoutParams = LinearLayout.LayoutParams(140, 140).apply { setMargins(0, 0, 0, 8) }
                setPadding(0, 0, 0, 0)
            }
            caption = TextView(this).apply {
                text = captionText
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = 12f
                setTextColor(if (isNavigationSelected(label)) Color.rgb(76, 232, 240) else Color.rgb(170, 177, 199))
                layoutParams = LinearLayout.LayoutParams(-1, 38)
            }
            row.addView(icon)
            row.addView(caption)
            row.background = rounded(if (isNavigationSelected(label)) 0x223FE7EF else 0x00111629, 12f)
            navItems.addView(row)
        }
    }

    private fun updateNavigationVisuals(focusedView: View?) {
        for (index in 0 until navItems.childCount) {
            val child = navItems.getChildAt(index)
            val row = child as? LinearLayout ?: continue
            val label = row.tag as? String ?: continue
            val active = if (focusedView != null) child === focusedView else isNavigationSelected(label)
            row.background = rounded(if (active) 0x333FE7EF else 0x00111629, 12f)
            (row.getChildAt(0) as? ImageView)?.alpha = if (active) 1f else 0.72f
            (row.getChildAt(1) as? TextView)?.setTextColor(
                if (active) Color.rgb(76, 232, 240) else Color.rgb(170, 177, 199)
            )
        }
    }

    private fun isNavigationSelected(label: String): Boolean = when {
        homeMode -> label == "INÍCIO"
        favoritesOnly -> label == "FAVORITOS"
        currentKind == MediaKind.LIVE -> label == "CANAIS"
        currentKind == MediaKind.MOVIE -> label == "FILMES"
        else -> label == "SÉRIES"
    }

    private fun showHome() {
        homeMode = true
        favoritesOnly = false
        homePanel.visibility = View.VISIBLE
        findViewById<View>(R.id.sideNavigation).visibility = View.GONE
        findViewById<View>(R.id.channelColumn).visibility = View.GONE
        findViewById<View>(R.id.previewScroll).visibility = View.GONE
        renderHomeHero()
    }

    private fun renderHomeHero() {
        homeHeroImage.setImageResource(R.drawable.excellence_home_hero)
        homeHeroTitle.text = "Aqui você encontra os melhores canais, filmes e séries"
        homeHeroDescription.text = "Conteúdos selecionados para você assistir com qualidade e praticidade."
    }

    private fun switchSection(kind: MediaKind) {
        homeMode = false
        homePanel.visibility = View.GONE
        findViewById<View>(R.id.sideNavigation).visibility = View.VISIBLE
        findViewById<View>(R.id.channelColumn).visibility = View.VISIBLE
        findViewById<View>(R.id.previewScroll).visibility = View.VISIBLE
        favoritesOnly = false
        currentKind = kind
        selectedCategory = "Todos"
        query = ""
        searchHint.text = searchPlaceholder()
        channelHeading.text = when (kind) {
            MediaKind.LIVE -> "CANAIS AO VIVO"
            MediaKind.MOVIE -> "FILMES"
            MediaKind.SERIES -> "SÉRIES"
        }
        renderNavigation()
        renderCategories()
        renderCatalog()
        selectFirstVisible()
    }

    private fun switchFavorites() {
        homeMode = false
        homePanel.visibility = View.GONE
        findViewById<View>(R.id.sideNavigation).visibility = View.VISIBLE
        findViewById<View>(R.id.channelColumn).visibility = View.VISIBLE
        findViewById<View>(R.id.previewScroll).visibility = View.VISIBLE
        favoritesOnly = true
        selectedCategory = "Todos"
        query = ""
        searchHint.text = "Buscar favoritos..."
        channelHeading.text = "FAVORITOS"
        renderNavigation()
        renderCategories()
        renderCatalog()
        selectFirstVisible()
    }

    private fun renderCategories() {
        if (databaseBackedCatalog) {
            categoryList.removeAllViews()
            renderCategoryButtons(listOf("Todos"))
            val requestKind = currentKind
            repository.queryGroups(requestKind, hiddenGroups()) { groups ->
                runOnUiThread {
                    if (databaseBackedCatalog && currentKind == requestKind) renderCategoryButtons(listOf("Todos") + groups)
                }
            }
            return
        }
        renderCategoryButtons(listOf("Todos") + currentItems().map { it.groupTitle.ifBlank { "Sem categoria" } }.distinct().sorted())
    }

    private fun renderCategoryButtons(categories: List<String>) {
        categoryList.removeAllViews()
        categories.forEach { category ->
            val item = TextView(this).apply {
                text = category
                gravity = Gravity.CENTER
                textSize = 10f
                isFocusable = true
                isClickable = true
                setPadding(12, 7, 12, 7)
                layoutParams = LinearLayout.LayoutParams(-2, -1).apply { setMargins(3, 0, 3, 0) }
                setOnClickListener {
                    selectedCategory = category
                    renderCategories()
                    renderCatalog()
                    selectFirstVisible()
                }
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) view.background = rounded(0x334CE8F0, 18f)
                }
            }
            item.setTextColor(if (category == selectedCategory) Color.rgb(76, 232, 240) else Color.rgb(170, 177, 199))
            item.background = rounded(if (category == selectedCategory) 0x334CE8F0 else 0x00111629, 18f)
            categoryList.addView(item)
        }
    }

    private fun renderCatalog() {
        if (!databaseBackedCatalog) {
            catalogAdapter.submit(visibleItems(), selectedEntry?.key)
            return
        }
        pageRequestId++
        pagedItems.clear()
        pageLoading = false
        pageFinished = false
        catalogAdapter.submit(emptyList(), selectedEntry?.key)
        loadNextPage()
    }

    private fun loadNextPage() {
        if (!databaseBackedCatalog || pageLoading || pageFinished) return
        pageLoading = true
        val requestId = pageRequestId
        val offset = pagedItems.size
        repository.queryPage(
            kind = if (favoritesOnly) null else currentKind,
            group = selectedCategory,
            search = query,
            hidden = hiddenGroups(),
            favorites = if (favoritesOnly) favorites() else emptySet(),
            sortAlphabetically = sortAlphabetically,
            limit = pageSize,
            offset = offset,
            seriesOnly = currentKind == MediaKind.SERIES && !favoritesOnly,
        ) { page ->
            runOnUiThread {
                if (requestId != pageRequestId) return@runOnUiThread
                pageLoading = false
                if (page.isEmpty()) {
                    pageFinished = true
                    if (offset == 0) selectedEntry = null
                    return@runOnUiThread
                }
                pagedItems.addAll(page)
                if (offset == 0) {
                    catalogAdapter.submit(pagedItems.toList(), selectedEntry?.key)
                    if (selectedEntry == null || pagedItems.none { it.key == selectedEntry?.key }) selectEntry(page.first(), false)
                } else {
                    catalogAdapter.append(page)
                }
                if (page.size < pageSize) pageFinished = true
            }
        }
    }

    private fun currentItems(): List<CatalogEntry> {
        if (favoritesOnly) return catalog.entries.filter { it.key in favorites() && !isHidden(it.groupTitle) }
        return catalog.entries.filter { it.kind == currentKind && !isHidden(it.groupTitle) }
    }

    private fun visibleItems(): List<CatalogEntry> {
        var result = currentItems()
        if (selectedCategory != "Todos") result = result.filter { it.groupTitle.ifBlank { "Sem categoria" } == selectedCategory }
        if (query.isNotBlank()) {
            val normalized = query.trim().lowercase()
            result = result.filter { item ->
                item.name.lowercase().contains(normalized) ||
                    item.groupTitle.lowercase().contains(normalized) ||
                    item.tvgId.lowercase().contains(normalized)
            }
        }
        return if (sortAlphabetically) result.sortedBy { it.name.lowercase() } else result
    }

    private fun selectFirstVisible() {
        if (databaseBackedCatalog) {
            val requestId = pageRequestId
            repository.queryPage(
                kind = if (favoritesOnly) null else currentKind,
                group = selectedCategory,
                search = query,
                hidden = hiddenGroups(),
                favorites = if (favoritesOnly) favorites() else emptySet(),
                sortAlphabetically = sortAlphabetically,
                limit = 1,
                offset = 0,
                seriesOnly = currentKind == MediaKind.SERIES && !favoritesOnly,
            ) { page ->
                runOnUiThread { if (requestId == pageRequestId) page.firstOrNull()?.let { selectEntry(it, false) } }
            }
            return
        }
        visibleItems().firstOrNull()?.let { selectEntry(it, false) }
    }

    private fun handleEntryClick(entry: CatalogEntry) {
        if (entry.kind == MediaKind.SERIES && entry.episode.isBlank() && !favoritesOnly) {
            selectEntry(entry, true)
            showSeriesSeasonsDialog(entry)
            return
        }
        val sameEntry = miniPlayerEntryKey == entry.key
        if (sameEntry && previewMode == PreviewMode.TRAILER) {
            startContentPreview(entry)
            return
        }
        if (sameEntry && previewMode == PreviewMode.CONTENT) {
            expandMiniPlayer()
            return
        }
        selectEntry(entry, true)
        if (entry.kind == MediaKind.MOVIE) {
            startTrailerPreview(entry)
        } else if (entry.kind == MediaKind.SERIES && entry.trailerUrl.isNotBlank()) {
            startTrailerPreview(entry)
        } else {
            startMiniPlayer(entry)
        }
    }

    private fun startContentPreview(entry: CatalogEntry) {
        if (entry.streamUrl.isBlank()) {
            Toast.makeText(this, "Este item não possui o filme/episódio disponível na lista do painel", Toast.LENGTH_SHORT).show()
            return
        }
        startMiniPlayer(entry, entry.streamUrl, entry.name)
    }

    private fun hasActiveMiniPreview(entry: CatalogEntry): Boolean =
        miniPlayerEntryKey == entry.key && (miniPlayer != null || miniTrailerView != null)

    private fun startMiniPlayer(entry: CatalogEntry, sourceUrl: String = entry.streamUrl, previewTitle: String = entry.name) {
        if (sourceUrl.isBlank()) {
            Toast.makeText(this, "Este item não possui uma transmissão válida", Toast.LENGTH_SHORT).show()
            return
        }
        stopMiniPlayer()
        val playerView = PlayerView(this).apply {
            useController = false
            controllerShowTimeoutMs = 0
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        videoPreview.addView(playerView, 1)
        val player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(sourceUrl))
        player.prepare()
        player.playWhenReady = true
        miniPlayer = player
        miniPlayerView = playerView
        miniPlayerEntryKey = entry.key
        previewMode = PreviewMode.CONTENT
        heroImage.visibility = View.GONE
        previewLogo.visibility = View.GONE
        liveBadge.visibility = if (entry.kind == MediaKind.LIVE) View.VISIBLE else View.GONE
        videoPreviewText.text = "Mini player • $previewTitle"
    }

    private fun startTrailerPreview(entry: CatalogEntry) {
        val trailer = entry.trailerUrl.trim()
        if (trailer.isBlank()) {
            startYoutubeTrailerSearchPreview(entry)
        } else if (trailer.contains("youtube.com", true) || trailer.contains("youtu.be", true)) {
            startYoutubeTrailerPreview(entry, trailer)
        } else {
            startMiniPlayer(entry, trailer, "Trailer • ${entry.name}")
        }
    }

    private fun startYoutubeTrailerSearchPreview(entry: CatalogEntry) {
        stopMiniPlayer()
        val query = Uri.encode("${entry.name} trailer oficial")
        val webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            loadUrl("https://www.youtube.com/embed?listType=search&list=$query&autoplay=1&controls=1&playsinline=1&rel=0")
        }
        videoPreview.addView(webView, 1)
        miniTrailerView = webView
        miniPlayerEntryKey = entry.key
        previewMode = PreviewMode.TRAILER
        heroImage.visibility = View.GONE
        previewLogo.visibility = View.GONE
        liveBadge.visibility = View.VISIBLE
        liveBadge.text = "TRAILER"
        videoPreviewText.text = "Trailer no YouTube • ${entry.name}"
    }

    private fun youtubeVideoId(value: String): String? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        return when {
            uri.host?.contains("youtu.be", true) == true -> uri.pathSegments.firstOrNull()
            uri.getQueryParameter("v").orEmpty().isNotBlank() -> uri.getQueryParameter("v")
            uri.pathSegments.contains("shorts") -> uri.pathSegments.getOrNull(uri.pathSegments.indexOf("shorts") + 1)
            uri.pathSegments.contains("embed") -> uri.pathSegments.getOrNull(uri.pathSegments.indexOf("embed") + 1)
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun startYoutubeTrailerPreview(entry: CatalogEntry, trailerUrl: String) {
        val videoId = youtubeVideoId(trailerUrl)
        if (videoId.isNullOrBlank()) {
            startYoutubeTrailerSearchPreview(entry)
            return
        }
        stopMiniPlayer()
        val webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&controls=1&playsinline=1&rel=0")
        }
        videoPreview.addView(webView, 1)
        miniTrailerView = webView
        miniPlayerEntryKey = entry.key
        previewMode = PreviewMode.TRAILER
        heroImage.visibility = View.GONE
        previewLogo.visibility = View.GONE
        liveBadge.visibility = View.VISIBLE
        liveBadge.text = "TRAILER"
        videoPreviewText.text = "Trailer • ${entry.name}"
    }

    private fun expandMiniPlayer() {
        val content = (miniPlayerView ?: miniTrailerView) ?: return
        val player = miniPlayer
        (content.parent as? ViewGroup)?.removeView(content)
        val fullScreen = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(content, FrameLayout.LayoutParams(-1, -1))
        }
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(fullScreen)
            setOnDismissListener {
                (content.parent as? ViewGroup)?.removeView(content)
                videoPreview.addView(content, 1)
                miniPlayerDialog = null
            }
        }
        miniPlayerDialog = dialog
        dialog.show()
        player?.playWhenReady = true
    }

    private fun stopMiniPlayer() {
        miniPlayerDialog?.setOnDismissListener(null)
        miniPlayerDialog?.dismiss()
        miniPlayerDialog = null
        miniPlayerView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        miniPlayerView = null
        miniTrailerView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.loadUrl("about:blank")
            it.destroy()
        }
        miniTrailerView = null
        miniPlayer?.release()
        miniPlayer = null
        miniPlayerEntryKey = null
        previewMode = PreviewMode.NONE
        if (::heroImage.isInitialized) heroImage.visibility = View.VISIBLE
        if (::previewLogo.isInitialized) previewLogo.visibility = View.GONE
    }

    private fun selectEntry(entry: CatalogEntry, requestFocus: Boolean) {
        if (selectedEntry?.key != entry.key) stopMiniPlayer()
        selectedEntry = entry
        val editorial = editorialFor(entry)
        val epgProgram = currentEpgProgram(entry)
        val isLive = entry.kind == MediaKind.LIVE
        val hasTrailer = entry.trailerUrl.isNotBlank()
        videoPreviewText.text = when {
            isLive -> "Preview • ${entry.name}"
            hasTrailer -> "▶  Trailer • ${entry.name}"
            else -> "Poster • ${entry.name}"
        }
        val heroSource = entry.backdropUrl.ifBlank { entry.logoUrl }
        heroImage.setImageResource(fallbackHero(entry))
        if (heroSource.isBlank()) {
            heroImage.setImageResource(fallbackHero(entry))
        } else {
            imageLoader.load(heroSource, heroImage, fallbackHero(entry))
        }
        previewLogo.visibility = View.GONE
        liveBadge.visibility = if (isLive || hasTrailer) View.VISIBLE else View.GONE
        liveBadge.text = if (isLive) "AO VIVO" else "TRAILER"
        detailEyebrow.text = if (isLive) editorial.eyebrow.uppercase() else kindLabel(entry.kind)
        detailChannelName.text = entry.name
        detailTags.text = listOf(entry.groupTitle, entry.year, entry.quality, kindLabel(entry.kind), entry.runtime)
            .filter { it.isNotBlank() }.joinToString("   •   ")
        aboutLabel.text = if (isLive) "SOBRE O CANAL" else if (entry.kind == MediaKind.MOVIE) "SOBRE O FILME" else "SOBRE A SÉRIE"
        detailDescription.text = if (isLive) editorial.description else entry.synopsis.ifBlank { "Sinopse não informada na lista do painel." }
        nowLabel.text = if (isLive) "AGORA" else if (entry.cast.isNotBlank()) "ELENCO" else "DETALHES"
        currentProgram.text = if (isLive) epgProgram?.title ?: editorial.currentProgram else entry.cast.ifBlank { "Elenco não informado na lista do painel." }
        currentProgramDescription.text = if (isLive) {
            epgProgram?.description?.ifBlank { null } ?: editorial.currentDescription
        } else if (hasTrailer) {
            "Trailer disponível. Toque no painel grande ou no botão Trailer para assistir."
        } else {
            "Selecione o item para assistir ao conteúdo da sua lista."
        }
        programTime.text = if (isLive) epgProgram?.let { "${formatTime(it.start)} – ${formatTime(it.stop)}" } ?: editorial.time
        else listOf(entry.year, entry.runtime).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "Informações da lista do painel" }
        nextProgram.text = if (isLive) {
            nextEpgProgram(entry)?.let { "A seguir  •  ${it.title}  •  ${formatTime(it.start)}" } ?: editorial.nextProgram
        } else if (hasTrailer) "▶  Assistir trailer" else "▶  Assistir conteúdo"
        renderActions(entry)
        if (requestFocus) channelList.requestFocus()
        if (!databaseBackedCatalog) renderCatalog() else catalogAdapter.submit(pagedItems.toList(), selectedEntry?.key)
    }

    private fun renderActions(entry: CatalogEntry) {
        actionRow.removeAllViews()
        val isFavorite = entry.key in favorites()
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        val primaryLabel = when {
            entry.kind == MediaKind.MOVIE -> "▶  TRAILER NO YOUTUBE"
            entry.kind == MediaKind.SERIES && entry.episode.isNotBlank() -> "▶  REPRODUZIR EPISÓDIO"
            else -> "▶  REPRODUZIR"
        }
        actions += primaryLabel to {
            val sameEntry = miniPlayerEntryKey == entry.key
            when {
                sameEntry && previewMode == PreviewMode.TRAILER -> startContentPreview(entry)
                sameEntry && previewMode == PreviewMode.CONTENT -> expandMiniPlayer()
                entry.kind == MediaKind.MOVIE -> startTrailerPreview(entry)
                entry.kind == MediaKind.SERIES && entry.episode.isNotBlank() && entry.trailerUrl.isNotBlank() -> startTrailerPreview(entry)
                else -> startMiniPlayer(entry)
            }
        }
        if (entry.kind == MediaKind.SERIES) {
            actions += "☷  TEMPORADAS" to { showSeriesSeasonsDialog(entry) }
        }
        actions += (if (isFavorite) "♥  Favorito" else "♡  Favoritar") to {
            toggleFavorite(entry)
            renderActions(entry)
            renderCatalog()
        }
        actions += "⌕  Buscar" to { showSearchDialog() }
        actions.forEachIndexed { index, (label, clickAction) ->
            val action = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                isFocusable = true
                isClickable = true
                setPadding(14, 10, 14, 10)
                setTextColor(if (index == 0) Color.rgb(5, 6, 10) else Color.WHITE)
                background = rounded(if (index == 0) 0xFF4CE8F0 else 0xFF1B2036, 8f)
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = rounded(if (index == 0 || hasFocus) 0xFF4CE8F0 else 0xFF1B2036, 8f)
                    (view as TextView).setTextColor(if (index == 0 || hasFocus) Color.rgb(5, 6, 10) else Color.WHITE)
                }
                setOnClickListener { clickAction() }
                layoutParams = LinearLayout.LayoutParams(-2, 44).apply { setMargins(0, 0, 8, 0) }
            }
            actionRow.addView(action)
        }
    }


    private fun openEntry(entry: CatalogEntry) {
        if (entry.streamUrl.isBlank()) {
            Toast.makeText(this, "Este item não possui URL válida na lista do painel", Toast.LENGTH_SHORT).show()
            loadRemoteConfiguration()
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_TITLE, entry.name)
            putExtra(PlayerActivity.EXTRA_URL, entry.streamUrl)
            putExtra(PlayerActivity.EXTRA_MAC, getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getString(PREF_MAC_ADDRESS, "").orEmpty())
        })
    }

    private fun showSeriesSeasonsDialog(entry: CatalogEntry) {
        seriesEpisodesDialog?.dismiss()
        seriesSeasonsDialog?.dismiss()
        val showTitle = seriesTitle(entry)
        val (dialog, list) = createCatalogDialog(
            title = showTitle,
            subtitle = "TEMPORADAS DISPONÍVEIS  •  selecione uma temporada para ver os episódios",
            onBack = null,
        )
        seriesSeasonsDialog = dialog
        dialog.setOnDismissListener { if (seriesSeasonsDialog === dialog) seriesSeasonsDialog = null }
        dialog.show()
        val render: (List<String>) -> Unit = { seasons ->
            list.removeAllViews()
            if (seasons.isEmpty()) {
                list.addView(dialogMessage("Nenhuma temporada identificada para esta série na lista do painel."))
            } else {
                seasons.forEach { season ->
                    list.addView(dialogButton("TEMPORADA ${season.padStart(2, '0')}") {
                        dialog.dismiss()
                        showSeriesEpisodesDialog(entry, season)
                    })
                }
            }
        }
        if (databaseBackedCatalog) {
            repository.querySeriesSeasons(showTitle, selectedCategory, hiddenGroups()) { seasons -> runOnUiThread { render(seasons) } }
        } else {
            render(currentItems().filter { it.kind == MediaKind.SERIES && seriesTitle(it) == showTitle }.map { it.season.ifBlank { "1" } }.distinct().sortedBy { it.toIntOrNull() ?: 1 })
        }
    }

    private fun showSeriesEpisodesDialog(entry: CatalogEntry, season: String) {
        seriesEpisodesDialog?.dismiss()
        val showTitle = seriesTitle(entry)
        val (dialog, list) = createCatalogDialog(
            title = showTitle,
            subtitle = "TEMPORADA ${season.padStart(2, '0')}  •  primeiro clique inicia o preview, segundo clique expande",
            onBack = { showSeriesSeasonsDialog(entry) },
        )
        seriesEpisodesDialog = dialog
        dialog.setOnDismissListener { if (seriesEpisodesDialog === dialog) seriesEpisodesDialog = null }
        dialog.show()
        val render: (List<CatalogEntry>) -> Unit = { episodes ->
            list.removeAllViews()
            if (episodes.isEmpty()) {
                list.addView(dialogMessage("Nenhum episódio encontrado nesta temporada."))
            } else {
                episodes.forEach { episode ->
                    val code = episode.episode.takeIf { it.isNotBlank() }?.let { "E${it.padStart(2, '0')}" } ?: "EP"
                    val episodeTitle = episode.name.removePrefix("${showTitle} ").trim().ifBlank { episode.name }
                    list.addView(dialogButton("$code  •  $episodeTitle") {
                        val sameEntry = miniPlayerEntryKey == episode.key
                        when {
                            sameEntry && previewMode == PreviewMode.TRAILER -> startContentPreview(episode)
                            sameEntry && previewMode == PreviewMode.CONTENT -> {
                                dialog.dismiss()
                                expandMiniPlayer()
                            }
                            else -> {
                                selectEntry(episode, false)
                                if (episode.trailerUrl.isNotBlank()) startTrailerPreview(episode) else startMiniPlayer(episode)
                            }
                        }
                    })
                }
            }
        }
        if (databaseBackedCatalog) {
            repository.querySeriesEpisodes(showTitle, season, selectedCategory, hiddenGroups()) { episodes -> runOnUiThread { render(episodes) } }
        } else {
            render(currentItems().filter {
                it.kind == MediaKind.SERIES && seriesTitle(it) == showTitle && (it.season.ifBlank { "1" } == season)
            }.sortedWith(compareBy({ it.episode.toIntOrNull() ?: Int.MAX_VALUE }, { it.name.lowercase() })))
        }
    }


    private fun createCatalogDialog(title: String, subtitle: String, onBack: (() -> Unit)?): Pair<Dialog, LinearLayout> {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = rounded(0xF00B1020, 18f)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitleView = TextView(this).apply {
            text = subtitle
            setTextColor(Color.rgb(143, 155, 184))
            textSize = 10f
            setPadding(0, dp(6), 0, 0)
        }
        heading.addView(titleView)
        heading.addView(subtitleView)
        header.addView(heading)
        val close = TextView(this).apply {
            text = if (onBack == null) "FECHAR" else "VOLTAR"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(0xFF1B2036, 8f)
            isFocusable = true
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                onBack?.invoke()
            }
        }
        header.addView(close, LinearLayout.LayoutParams(-2, dp(42)).apply { setMargins(dp(10), 0, 0, 0) })
        root.addView(header)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(list, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.72f).toInt(),
                (resources.displayMetrics.heightPixels * 0.78f).toInt(),
            )
            close.requestFocus()
        }
        return dialog to list
    }

    private fun dialogButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE)
        textSize = 14f
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(18), dp(12), dp(18), dp(12))
        isFocusable = true
        isClickable = true
        background = rounded(0xFF161D33, 10f)
        setOnFocusChangeListener { view, hasFocus ->
            view.background = rounded(if (hasFocus) 0xFF286B7A else 0xFF161D33, 10f)
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, 0, 0, dp(8)) }
    }

    private fun dialogMessage(message: String): TextView = TextView(this).apply {
        text = message
        setTextColor(Color.rgb(170, 177, 199))
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(28), dp(18), dp(28))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun seriesTitle(entry: CatalogEntry): String = entry.seriesGroup.ifBlank { entry.name }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Nome do canal, filme ou série"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
        }
        AlertDialog.Builder(this)
            .setTitle("Buscar no catálogo")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Buscar") { _, _ ->
                query = input.text.toString()
                searchHint.text = if (query.isBlank()) searchPlaceholder() else query
                renderCatalog()
                selectFirstVisible()
            }
            .show()
    }

    private fun loadRemoteConfiguration() {
        val mac = getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getString(PREF_MAC_ADDRESS, "").orEmpty()
        if (mac.isBlank()) return
        appIntegration.fetchConfig(mac) { result ->
            runOnUiThread {
                result.onSuccess { config ->
                    if (!config.registered || !config.allowed) {
                        showAccessUnavailable(config)
                        return@onSuccess
                    }
                    applyRemoteConfig(config)
                    appIntegration.startBackgroundSync(
                        mac = mac,
                        currentContent = { selectedEntry?.name },
                        onNotifications = { notifications -> showRemoteNotifications(mac, notifications) },
                        onCommands = { commands -> showRemoteCommands(mac, commands) },
                    )
                }.onFailure {
                    Toast.makeText(this, "Configuração remota indisponível; mantendo a última lista válida", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyRemoteConfig(config: RemoteAppConfig) {
        brandMark.text = "EXCELLENCE"
        brandSubtitle.text = "TV PLAYER"
        appLogo.setImageResource(R.drawable.excellence_logo)
        if (config.backgroundUrl.isNotBlank()) imageLoader.load(config.backgroundUrl, remoteBackground, R.drawable.excellence_logo)
        if (config.bannerUrl.isNotBlank()) remoteBannerUrl = config.bannerUrl
        if (config.epgUrl.isNotBlank()) remoteEpgUrl = config.epgUrl
        if (config.dnsUrl.isNotBlank() || config.serverApiUrl.isNotBlank() || config.testApiUrl.isNotBlank()) {
            getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_SERVER_API_URL, config.dnsUrl.ifBlank { config.serverApiUrl })
                .putString(PREF_TEST_API_URL, config.testApiUrl)
                .apply()
        }
        if (config.messageTitle.isNotBlank() || config.messageText.isNotBlank()) {
            val messageKey = "${config.messageTitle}|${config.messageText}"
            val shownKey = getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getString(PREF_LAST_MESSAGE_KEY, "")
            if (messageKey != shownKey) {
                getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_LAST_MESSAGE_KEY, messageKey).apply()
                AlertDialog.Builder(this)
                    .setTitle(config.messageTitle.ifBlank { "Aviso" })
                    .setMessage(config.messageText)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        if (config.playlistUrls.isNotEmpty()) {
            repository.loadIfChanged(config.playlistUrls) { result ->
                runOnUiThread {
                    result.onSuccess { loaded -> applyCatalogSnapshot(loaded, config) }
                        .onFailure { showCatalogUnavailable("A lista do painel não está disponível nesta TV Box.") }
                }
            }
        } else {
            showCatalogUnavailable("O painel liberou o MAC, mas não enviou nenhuma lista.")
        }
        if (config.apkVersion.isNotBlank() && config.apkDownloadUrl.isNotBlank() && config.apkVersion != packageManager.getPackageInfo(packageName, 0).versionName) {
            Toast.makeText(this, "Há uma atualização disponível: ${config.apkVersion}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyCatalogSnapshot(snapshot: CatalogSnapshot, config: RemoteAppConfig) {
        if (snapshot.totalCount <= 0) {
            showCatalogUnavailable("A lista do painel foi recebida vazia.")
            return
        }
        catalog = snapshot
        databaseBackedCatalog = snapshot.databaseBacked
        greeting.text = "Olá, usuário  •  ${snapshot.totalCount} itens"
        currentKind = MediaKind.LIVE
        favoritesOnly = false
        selectedCategory = "Todos"
        renderNavigation()
        renderCategories()
        renderCatalog()
        selectFirstVisible()
        loadConfiguredEpg(config.epgUrl.ifBlank { config.playlistUrls.firstOrNull().orEmpty() })
        renderVodStrip()
    }

    private fun renderVodStrip() {
        vodCards.removeAllViews()
        if (!databaseBackedCatalog) return
        repository.queryPage(MediaKind.MOVIE, "Todos", "", hiddenGroups(), emptySet(), sortAlphabetically, 4, 0) { movies ->
            runOnUiThread {
                movies.forEach { movie ->
                    val card = TextView(this).apply {
                        text = movie.name
                        gravity = Gravity.CENTER_VERTICAL
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        setPadding(12, 6, 12, 6)
                        background = rounded(0x661B2036, 10f)
                        isFocusable = true
                        isClickable = true
                        layoutParams = LinearLayout.LayoutParams(150, 52).apply { setMargins(0, 0, 8, 0) }
                        setOnClickListener { selectEntry(movie, true) }
                    }
                    vodCards.addView(card)
                }
            }
        }
    }

    private fun showCatalogUnavailable(message: String) {
        databaseBackedCatalog = false
        catalog = CatalogSnapshot(emptyList())
        pagedItems.clear()
        selectedEntry = null
        renderCategories()
        renderCatalog()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showAccessUnavailable(config: RemoteAppConfig) {
        AlertDialog.Builder(this)
            .setTitle("Acesso indisponível")
            .setMessage("Este dispositivo não está autorizado para Excellence. Verifique o MAC e o cadastro no painel.")
            .setPositiveButton("Configurar MAC") { _, _ -> showMacDialog() }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showRemoteNotifications(mac: String, notifications: List<RemoteNotification>) {
        notifications.forEach { notification ->
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(notification.title.ifBlank { "Aviso" })
                    .setMessage(notification.message)
                    .setPositiveButton("OK") { _, _ -> appIntegration.ackNotification(mac, notification.id) }
                    .show()
            }
        }
    }

    private fun showRemoteCommands(mac: String, commands: List<RemoteCommand>) {
        commands.forEach { command ->
            runOnUiThread { executeRemoteCommand(mac, command) }
        }
    }

    private fun executeRemoteCommand(mac: String, command: RemoteCommand) {
        when (command.command.lowercase()) {
            "refresh_playlist" -> {
                loadRemoteConfiguration()
                appIntegration.ackCommand(mac, command.id, "executed", "Playlist atualizada pelo painel")
            }
            "switch_playlist" -> {
                val url = command.payload.optString("url")
                if (url.startsWith("http", true)) {
                    repository.load(listOf(url)) { result ->
                        result.onSuccess { loaded ->
                            runOnUiThread {
                                catalog = loaded
                                renderCategories()
                                renderCatalog()
                                selectFirstVisible()
                            }
                            appIntegration.ackCommand(mac, command.id, "executed", "Playlist alternada")
                        }.onFailure { appIntegration.ackCommand(mac, command.id, "failed", it.message.orEmpty()) }
                    }
                } else {
                    appIntegration.ackCommand(mac, command.id, "failed", "URL de playlist ausente")
                }
            }
            "update_dns" -> {
                val dns = command.payload.optString("dns", command.payload.optString("url"))
                if (dns.startsWith("http", true)) {
                    getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_SERVER_API_URL, dns).apply()
                    appIntegration.ackCommand(mac, command.id, "executed", "DNS aplicada")
                } else {
                    appIntegration.ackCommand(mac, command.id, "failed", "DNS ausente")
                }
            }
            "show_message" -> {
                AlertDialog.Builder(this)
                    .setTitle(command.payload.optString("title", "Aviso"))
                    .setMessage(command.payload.optString("message"))
                    .setPositiveButton("OK") { _, _ -> appIntegration.ackCommand(mac, command.id, "executed", "Mensagem exibida") }
                    .show()
            }
            "sync_access" -> {
                appIntegration.ackCommand(mac, command.id, "executed", "Acesso sincronizado")
                loadRemoteConfiguration()
            }
            "restart_player" -> appIntegration.ackCommand(mac, command.id, "executed", "Sessão pronta para reiniciar")
            else -> appIntegration.ackCommand(mac, command.id, "failed", "Comando não suportado")
        }
    }

    private fun showSettingsDialog() {
        val message = "Catálogo recebido do painel pelo MAC.\\n\\n${catalog.totalCount} itens disponíveis em ${catalog.groupCount} grupos."
        AlertDialog.Builder(this)
            .setTitle("Ajustes do Excellence")
            .setMessage(message)
            .setItems(arrayOf("Categorias ocultas e ordem", "Recarregar catálogo do painel", "Testar API do Servidor", "Limpar cache local")) { _, which ->
                when (which) {
                    0 -> showCatalogRulesDialog()
                    1 -> loadRemoteConfiguration()
                    2 -> showServerTestDialog()
                    3 -> {
                        repository.clearCache()
                        Toast.makeText(this, "Cache local limpo; a próxima consulta virá do painel", Toast.LENGTH_SHORT).show()
                        loadRemoteConfiguration()
                    }
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showCatalogRulesDialog() {
        val groupInput = EditText(this).apply {
            hint = "Ex.: ADULTOS, RADIOS"
            setSingleLine(false)
            setText(hiddenGroups().joinToString(", "))
        }
        val sortCheck = CheckBox(this).apply {
            text = "Ordenar itens alfabeticamente"
            isChecked = sortAlphabetically
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 0, 28, 0)
            addView(TextView(this@MainActivity).apply { text = "Grupos ocultos, separados por vírgula:" })
            addView(groupInput)
            addView(sortCheck)
        }
        AlertDialog.Builder(this)
            .setTitle("Categorias e ordem")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val groups = groupInput.text.toString().split(",", "\\n").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).edit()
                    .putStringSet(PREF_HIDDEN_GROUPS, groups)
                    .putBoolean(PREF_SORT_ALPHA, sortCheck.isChecked)
                    .apply()
                sortAlphabetically = sortCheck.isChecked
                renderCategories()
                renderCatalog()
                selectFirstVisible()
            }
            .show()
    }

    private fun showServerTestDialog() {
        val mac = getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getString(PREF_MAC_ADDRESS, "").orEmpty()
        val apiUrl = getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getString(PREF_TEST_API_URL, "").orEmpty()
        if (apiUrl.isBlank()) {
            Toast.makeText(this, "A API do Servidor ainda não foi configurada no painel", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Testando API do Servidor...", Toast.LENGTH_SHORT).show()
        appIntegration.testExternalApi(apiUrl) { result ->
            runOnUiThread {
                result.onSuccess { test ->
                    val status = if (test.ok) "online" else "offline"
                    val payload = JSONObject().apply {
                        put("mac", mac)
                        put("name", brandMark.text.toString())
                        put("status", status)
                        put("source", "maximus")
                    }
                    appIntegration.reportMaximusTestResult(payload)
                    AlertDialog.Builder(this)
                        .setTitle("Teste da API do Servidor")
                        .setMessage("Status: $status\\nHTTP: ${test.httpCode}\\n${test.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("Falha no teste")
                        .setMessage(it.message ?: "Não foi possível testar a API")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun showMacDialog() {
        val input = EditText(this).apply {
            hint = "AA:BB:CC:DD:EE:FF"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getString(PREF_MAC_ADDRESS, ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Dispositivo / MAC")
            .setMessage("O MAC fica salvo somente neste dispositivo e pode ser usado pelo seu servidor autorizado.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_MAC_ADDRESS, input.text.toString().trim()).apply()
                Toast.makeText(this, "MAC salvo", Toast.LENGTH_SHORT).show()
                loadRemoteConfiguration()
            }
            .show()
    }

    private fun loadDerivedEpg(m3uUrl: String?) {
        val epgUrl = m3uUrl?.takeIf { it.contains("get.php", true) }?.replace("get.php", "xmltv.php", ignoreCase = true) ?: return
        loadConfiguredEpg(epgUrl)
    }

    private fun loadConfiguredEpg(epgUrl: String?) {
        if (epgUrl.isNullOrBlank()) return
        val resolved = if (epgUrl.contains("get.php", true)) epgUrl.replace("get.php", "xmltv.php", ignoreCase = true) else epgUrl
        epgRepository.load(resolved) { result ->
            result.onSuccess { map ->
                runOnUiThread {
                    epgByChannel = map
                    selectedEntry?.let { selectEntry(it, false) }
                }
            }
        }
    }

    private fun currentEpgProgram(entry: CatalogEntry): EpgProgram? {
        val programs = epgByChannel[entry.tvgId].orEmpty()
        if (programs.isEmpty()) return null
        val now = System.currentTimeMillis()
        return programs.firstOrNull { now in it.start..it.stop } ?: programs.firstOrNull { it.start > now }
    }

    private fun nextEpgProgram(entry: CatalogEntry): EpgProgram? {
        val now = System.currentTimeMillis()
        return epgByChannel[entry.tvgId].orEmpty().firstOrNull { it.start > now }
    }

    private fun formatTime(timestamp: Long): String = if (timestamp <= 0L) "--:--" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    private fun hiddenGroups(): Set<String> = getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getStringSet(PREF_HIDDEN_GROUPS, emptySet())?.map { it.uppercase() }?.toSet() ?: emptySet()

    private fun isHidden(group: String): Boolean = group.uppercase() in hiddenGroups()

    private fun favorites(): MutableSet<String> {
        return getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).getStringSet(PREF_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun toggleFavorite(entry: CatalogEntry) {
        val current = favorites()
        if (!current.add(entry.key)) current.remove(entry.key)
        getSharedPreferences(ActivationActivity.PREFS_NAME, MODE_PRIVATE).edit().putStringSet(PREF_FAVORITES, current).apply()
    }

    private fun editorialFor(entry: CatalogEntry): ChannelEditorial {
        val known = editorials.entries.firstOrNull { entry.name.lowercase().contains(it.key) }?.value
        return known ?: ChannelEditorial(
            eyebrow = kindLabel(entry.kind),
            description = "${entry.name} está disponível na categoria ${entry.groupTitle}. Selecione assistir agora para abrir o conteúdo.",
            tags = "${entry.groupTitle}   •   ${kindLabel(entry.kind)}",
            currentProgram = if (entry.kind == MediaKind.LIVE) "Programação ao vivo" else entry.name,
            currentDescription = "Informações detalhadas do programa serão exibidas quando o provedor disponibilizar EPG para este item.",
            time = if (entry.kind == MediaKind.LIVE) "Ao vivo" else "Disponível para assistir",
            nextProgram = "A seguir  •  Consulte a programação do provedor",
        )
    }

    private fun kindLabel(kind: MediaKind): String = when (kind) {
        MediaKind.LIVE -> "AO VIVO"
        MediaKind.MOVIE -> "FILME"
        MediaKind.SERIES -> "SÉRIE"
    }

    private fun fallbackLogo(entry: CatalogEntry): Int = when {
        entry.name.contains("Animal Planet", true) -> R.drawable.animal_planet_logo
        entry.name.contains("Cartoon Network", true) -> R.drawable.cartoon_network_logo
        entry.name.contains("Discovery", true) -> R.drawable.discovery_logo
        entry.name.contains("National Geographic", true) -> R.drawable.national_geo_logo
        entry.name.contains("ESPN", true) -> R.drawable.espn_logo
        else -> R.drawable.tv_banner
    }

    private fun fallbackHero(entry: CatalogEntry): Int = when {
        entry.name.contains("Animal Planet", true) -> R.drawable.animal_planet_hero
        entry.name.contains("Cartoon Network", true) -> R.drawable.cartoon_network_hero
        entry.name.contains("Discovery", true) -> R.drawable.discovery_hero
        entry.name.contains("National Geographic", true) -> R.drawable.national_geo_hero
        entry.name.contains("ESPN", true) -> R.drawable.espn_hero
        else -> R.drawable.excellence_home_hero
    }

    private fun searchPlaceholder(): String = when {
        favoritesOnly -> "Buscar favoritos..."
        currentKind == MediaKind.MOVIE -> "Buscar filme..."
        currentKind == MediaKind.SERIES -> "Buscar série..."
        else -> "Buscar canal..."
    }

    private fun rounded(color: Long, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb((color shr 24 and 0xFF).toInt(), (color shr 16 and 0xFF).toInt(), (color shr 8 and 0xFF).toInt(), (color and 0xFF).toInt()))
        cornerRadius = radius
    }

    override fun onDestroy() {
        stopMiniPlayer()
        repository.shutdown()
        imageLoader.shutdown()
        epgRepository.shutdown()
        appIntegration.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val PREF_FAVORITES = "favorite_catalog_keys"
        private const val PREF_HIDDEN_GROUPS = "hidden_catalog_groups"
        private const val PREF_SORT_ALPHA = "catalog_sort_alpha"
        private const val PREF_MAC_ADDRESS = "mac_address"
        private const val PREF_SERVER_API_URL = "server_api_url"
        private const val PREF_TEST_API_URL = "test_api_url"
        private const val PREF_LAST_MESSAGE_KEY = "last_remote_message_key"
    }
}

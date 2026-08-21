package com.maximus.tvplayer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

private data class TvChannel(
    val name: String,
    val logoRes: Int,
    val heroRes: Int,
    val category: String,
    val eyebrow: String,
    val description: String,
    val tags: String,
    val currentProgram: String,
    val currentDescription: String,
    val time: String,
    val nextProgram: String,
)

class MainActivity : Activity() {
    private lateinit var channelList: LinearLayout
    private lateinit var categoryList: LinearLayout
    private lateinit var navItems: LinearLayout
    private lateinit var videoPreviewText: TextView
    private lateinit var heroImage: ImageView
    private lateinit var liveBadge: TextView
    private lateinit var detailEyebrow: TextView
    private lateinit var detailChannelName: TextView
    private lateinit var detailTags: TextView
    private lateinit var detailDescription: TextView
    private lateinit var currentProgram: TextView
    private lateinit var currentProgramDescription: TextView
    private lateinit var programTime: TextView
    private lateinit var nextProgram: TextView
    private lateinit var actionRow: LinearLayout

    private val channels = listOf(
        TvChannel(
            name = "Animal Planet",
            logoRes = R.drawable.animal_planet_logo,
            heroRes = R.drawable.animal_planet_hero,
            category = "Documentários",
            eyebrow = "Natureza e vida selvagem",
            description = "Documentários, expedições e histórias sobre animais, seus habitats e a relação entre as pessoas e o mundo natural.",
            tags = "Animais   •   Natureza   •   Documentários",
            currentProgram = "Explorando a Selva",
            currentDescription = "Uma expedição acompanha espécies e paisagens selvagens em diferentes regiões do planeta.",
            time = "12:51 – 13:42",
            nextProgram = "A seguir  •  Predadores do Mundo  •  13:42",
        ),
        TvChannel(
            name = "Discovery Channel",
            logoRes = R.drawable.discovery_logo,
            heroRes = R.drawable.discovery_hero,
            category = "Documentários",
            eyebrow = "Ciência, aventura e descoberta",
            description = "Séries e documentários que exploram ciência, tecnologia, engenharia, aventura e os mistérios do mundo.",
            tags = "Ciência   •   Aventura   •   Documentários",
            currentProgram = "Mestres da Engenharia",
            currentDescription = "Projetos impressionantes e as pessoas que transformam grandes ideias em realidade.",
            time = "12:30 – 13:30",
            nextProgram = "A seguir  •  Sobrevivência Extrema  •  13:30",
        ),
        TvChannel(
            name = "National Geographic",
            logoRes = R.drawable.national_geo_logo,
            heroRes = R.drawable.national_geo_hero,
            category = "Documentários",
            eyebrow = "Conhecimento e exploração",
            description = "Produções sobre ciência, história, cultura, viagens e vida selvagem com imagens de diferentes lugares do planeta.",
            tags = "Ciência   •   Viagens   •   Natureza",
            currentProgram = "Segredos do Oceano",
            currentDescription = "Uma jornada pelas profundezas do mar revela comportamentos e ambientes ainda pouco conhecidos.",
            time = "12:10 – 13:20",
            nextProgram = "A seguir  •  Grandes Civilizações  •  13:20",
        ),
        TvChannel(
            name = "ESPN Brasil",
            logoRes = R.drawable.espn_logo,
            heroRes = R.drawable.espn_hero,
            category = "Esportes",
            eyebrow = "Esportes e competição",
            description = "Eventos esportivos ao vivo, programas de debate, notícias e análises para acompanhar os principais campeonatos.",
            tags = "Esportes   •   Ao vivo   •   Análises",
            currentProgram = "ESPN na Área",
            currentDescription = "Notícias, comentários e os principais destaques esportivos do dia.",
            time = "12:00 – 13:00",
            nextProgram = "A seguir  •  Linha de Passe  •  13:00",
        ),
        TvChannel(
            name = "Cartoon Network",
            logoRes = R.drawable.cartoon_network_logo,
            heroRes = R.drawable.cartoon_network_hero,
            category = "Infantil",
            eyebrow = "Desenhos e diversão",
            description = "Animações, aventuras e personagens para a família acompanhar ao longo do dia.",
            tags = "Infantil   •   Animação   •   Família",
            currentProgram = "Hora de Aventura",
            currentDescription = "Aventuras, humor e amizade em um mundo cheio de personagens inesquecíveis.",
            time = "12:40 – 13:10",
            nextProgram = "A seguir  •  O Mundo de Greg  •  13:10",
        ),
    )

    private var selectedChannel: TvChannel? = null
    private var selectedCategory = "Todos"

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
        bindViews()
        renderNavigation()
        renderCategories()
        renderChannels()
        if (channels.isNotEmpty()) selectChannel(channels.first(), requestFocus = false)
    }

    private fun bindViews() {
        channelList = findViewById(R.id.channelList)
        categoryList = findViewById(R.id.categoryList)
        navItems = findViewById(R.id.navItems)
        videoPreviewText = findViewById(R.id.videoPreviewText)
        heroImage = findViewById(R.id.heroImage)
        liveBadge = findViewById(R.id.liveBadge)
        detailEyebrow = findViewById(R.id.detailEyebrow)
        detailChannelName = findViewById(R.id.detailChannelName)
        detailTags = findViewById(R.id.detailTags)
        detailDescription = findViewById(R.id.detailDescription)
        currentProgram = findViewById(R.id.currentProgram)
        currentProgramDescription = findViewById(R.id.currentProgramDescription)
        programTime = findViewById(R.id.programTime)
        nextProgram = findViewById(R.id.nextProgram)
        actionRow = findViewById(R.id.actionRow)
    }

    private fun renderNavigation() {
        val items = listOf("INÍCIO", "CANAIS", "FILMES", "SÉRIES", "FAVORITOS", "AJUSTES")
        items.forEachIndexed { index, label ->
            val item = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(if (index == 1) Color.rgb(76, 232, 240) else Color.rgb(170, 177, 199))
                textSize = 10f
                isFocusable = true
                isClickable = true
                setPadding(4, 12, 4, 12)
                layoutParams = LinearLayout.LayoutParams(-1, 58).apply {
                    setMargins(8, 2, 8, 2)
                }
                background = rounded(if (index == 1) 0x223FE7EF else 0x00111629, 10f)
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = rounded(if (hasFocus || index == 1) 0x333FE7EF else 0x00111629, 10f)
                    if (hasFocus) (view as TextView).setTextColor(Color.rgb(76, 232, 240))
                }
            }
            navItems.addView(item)
        }
    }

    private fun renderCategories() {
        categoryList.removeAllViews()
        listOf("Todos", "Documentários", "Esportes", "Infantil").forEach { category ->
            val item = TextView(this).apply {
                text = category
                gravity = Gravity.CENTER
                textSize = 10f
                isFocusable = true
                isClickable = true
                setPadding(12, 7, 12, 7)
                setOnClickListener {
                    selectedCategory = category
                    renderCategories()
                    renderChannels()
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

    private fun renderChannels() {
        channelList.removeAllViews()
        val visible = if (selectedCategory == "Todos") channels else channels.filter { it.category == selectedCategory }
        visible.forEachIndexed { index, channel ->
            val row = LinearLayout(this).apply {
                tag = channel.name
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(10, 8, 10, 8)
                background = rounded(if (selectedChannel?.name == channel.name) 0x333FE7EF else 0x00111629, 10f)
                layoutParams = LinearLayout.LayoutParams(-1, 62).apply { setMargins(0, 3, 0, 3) }
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) selectChannel(channel, requestFocus = false)
                    view.background = rounded(if (hasFocus || selectedChannel?.name == channel.name) 0x333FE7EF else 0x00111629, 10f)
                }
                setOnClickListener { selectChannel(channel, requestFocus = false) }
            }

            val number = TextView(this).apply {
                text = String.format("%02d", index + 1)
                setTextColor(Color.rgb(111, 120, 149))
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(34, -1)
            }
            val channelName = TextView(this).apply {
                text = channel.name
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            }
            val live = TextView(this).apply {
                text = "LIVE"
                setTextColor(Color.rgb(244, 123, 156))
                textSize = 8f
                setPadding(5, 3, 5, 3)
                background = rounded(0x33F47B9C, 4f)
            }
            val logo = ImageView(this).apply {
                setImageResource(channel.logoRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(42, 42).apply { setMargins(0, 0, 8, 0) }
            }
            row.addView(logo)
            row.addView(number)
            row.addView(channelName)
            row.addView(live)
            channelList.addView(row)
        }
    }

    private fun selectChannel(channel: TvChannel, requestFocus: Boolean) {
        selectedChannel = channel
        videoPreviewText.text = "Preview • ${channel.name}"
        heroImage.setImageResource(channel.heroRes)
        liveBadge.visibility = View.VISIBLE
        detailEyebrow.text = channel.eyebrow.uppercase()
        detailChannelName.text = channel.name
        detailTags.text = channel.tags
        detailDescription.text = channel.description
        currentProgram.text = channel.currentProgram
        currentProgramDescription.text = channel.currentDescription
        programTime.text = channel.time
        nextProgram.text = channel.nextProgram
        renderActions(channel)
        if (requestFocus) channelList.requestFocus()
        for (i in 0 until channelList.childCount) {
            val child = channelList.getChildAt(i)
            child.background = rounded(
                if (child.tag == channel.name) 0x333FE7EF else 0x00111629,
                10f,
            )
        }
    }

    private fun renderActions(channel: TvChannel) {
        actionRow.removeAllViews()
        val actions = listOf("▶  Assistir agora", "♡  Favoritar", "⌕  Buscar")
        actions.forEachIndexed { index, label ->
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
                setOnClickListener {
                    if (index == 0) Toast.makeText(this@MainActivity, "Abrindo ${channel.name}...", Toast.LENGTH_SHORT).show()
                    if (index == 1) Toast.makeText(this@MainActivity, "${channel.name} adicionado aos favoritos", Toast.LENGTH_SHORT).show()
                    if (index == 2) Toast.makeText(this@MainActivity, "Busca de programação disponível em breve", Toast.LENGTH_SHORT).show()
                }
                layoutParams = LinearLayout.LayoutParams(-2, 44).apply { setMargins(0, 0, 8, 0) }
            }
            actionRow.addView(action)
        }
    }

    private fun rounded(color: Long, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb((color shr 24 and 0xFF).toInt(), (color shr 16 and 0xFF).toInt(), (color shr 8 and 0xFF).toInt(), (color and 0xFF).toInt()))
        cornerRadius = radius
    }
}

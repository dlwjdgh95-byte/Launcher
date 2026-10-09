package app.monolauncher.ui.settings

/** Variables every setup needs, listed first even before a routine references them. */
val BASE_VARIABLES = listOf("home_lat", "home_lng")

private val PLACEHOLDER = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\}""")
private val ALIAS_SEPARATORS = charArrayOf(',', '，', '、')

/** "지도, 카맵,," -> ["지도", "카맵"] */
fun parseAliases(text: String): List<String> =
    text.split(*ALIAS_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** [BASE_VARIABLES] followed by every other `{placeholder}` in [routinesJson], in order of appearance. */
fun routineVariableNames(routinesJson: String): List<String> =
    (BASE_VARIABLES + PLACEHOLDER.findAll(routinesJson).map { it.groupValues[1] }).distinct()

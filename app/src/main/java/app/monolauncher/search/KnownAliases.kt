package app.monolauncher.search

/**
 * Korean names for apps whose launcher label stays in English on a Korean-locale device,
 * so '유튜브' finds YouTube Music. Merged with the user's own aliases by [SearchEngine].
 */
internal object KnownAliases {
    private val BY_PACKAGE: Map<String, List<String>> = mapOf(
        "com.google.android.apps.youtube.music" to listOf("유튜브 뮤직", "유튭 뮤직"),
        "com.google.android.youtube" to listOf("유튜브", "유튭"),
        "com.google.android.apps.chromecast.app" to listOf("구글 홈"),
        "net.daum.android.map" to listOf("카카오지도", "카맵"),
        "com.kakao.talk" to listOf("카톡"),
        "com.google.android.apps.maps" to listOf("구글 지도", "구글맵"),
        "com.google.android.googlequicksearchbox" to listOf("구글"),
        "com.android.chrome" to listOf("크롬"),
        "com.google.android.gm" to listOf("지메일"),
    )

    fun forPackage(packageName: String): List<String> = BY_PACKAGE[packageName].orEmpty()
}

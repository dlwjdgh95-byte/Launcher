package app.monolauncher.apps

import android.content.ComponentName
import android.os.UserHandle
import app.monolauncher.search.SearchItem

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    val packageName: String get() = component.packageName
    val key: String get() = "${component.flattenToString()}#${user.hashCode()}"

    fun toSearchItem(aliases: List<String> = emptyList()) =
        SearchItem(key = key, label = label, packageName = packageName, aliases = aliases)
}

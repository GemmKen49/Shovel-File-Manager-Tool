package com.affilemanager.app.data

import android.content.Context
import com.affilemanager.app.model.EntryKind
import org.json.JSONArray
import org.json.JSONObject

enum class SearchScopePreference { CURRENT_FOLDER, ALL_STORAGE, SELECTED_STORAGE }

data class SearchDraftPreferences(
    val scope: SearchScopePreference = SearchScopePreference.CURRENT_FOLDER,
    val selectedStoragePaths: Set<String> = emptySet(),
    val includeHidden: Boolean = false,
    val useRegex: Boolean = false,
    val matchExtension: Boolean = false,
    val searchContents: Boolean = false,
    val kinds: Set<EntryKind> = emptySet(),
    val minimumMiB: String = "",
    val maximumMiB: String = "",
    val newerThanDays: Int? = null,
    val olderThanDays: Int? = null,
    val advancedExpanded: Boolean = false,
)

data class RecentViewPreferences(
    val sort: String = "RECENT",
    val ascending: Boolean = false,
    val dateRange: String = "ALL",
)

/** Persists non-secret screen choices. Temporary passwords and pairing codes never enter this store. */
class UiPreferenceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSearchDraft(): SearchDraftPreferences = runCatching {
        val json = JSONObject(preferences.getString(KEY_SEARCH, null) ?: return@runCatching SearchDraftPreferences())
        UiPreferenceRules.normalizeSearch(
            SearchDraftPreferences(
                scope = enumValueOrDefault(json.optString("scope"), SearchScopePreference.CURRENT_FOLDER),
                selectedStoragePaths = json.stringSet("selectedStoragePaths"),
                includeHidden = json.optBoolean("includeHidden"),
                useRegex = json.optBoolean("useRegex"),
                matchExtension = json.optBoolean("matchExtension"),
                searchContents = json.optBoolean("searchContents"),
                kinds = json.stringSet("kinds").mapNotNullTo(linkedSetOf()) { enumValueOrNull<EntryKind>(it) },
                minimumMiB = json.optString("minimumMiB"),
                maximumMiB = json.optString("maximumMiB"),
                newerThanDays = json.optionalInt("newerThanDays"),
                olderThanDays = json.optionalInt("olderThanDays"),
                advancedExpanded = json.optBoolean("advancedExpanded"),
            ),
        )
    }.getOrElse { SearchDraftPreferences() }

    fun saveSearchDraft(value: SearchDraftPreferences) {
        val normalized = UiPreferenceRules.normalizeSearch(value)
        val json = JSONObject()
            .put("scope", normalized.scope.name)
            .put("selectedStoragePaths", normalized.selectedStoragePaths.toJsonArray())
            .put("includeHidden", normalized.includeHidden)
            .put("useRegex", normalized.useRegex)
            .put("matchExtension", normalized.matchExtension)
            .put("searchContents", normalized.searchContents)
            .put("kinds", normalized.kinds.map(EntryKind::name).toJsonArray())
            .put("minimumMiB", normalized.minimumMiB)
            .put("maximumMiB", normalized.maximumMiB)
            .put("newerThanDays", normalized.newerThanDays ?: JSONObject.NULL)
            .put("olderThanDays", normalized.olderThanDays ?: JSONObject.NULL)
            .put("advancedExpanded", normalized.advancedExpanded)
        check(preferences.edit().putString(KEY_SEARCH, json.toString()).commit()) {
            "Nustatymų įrašyti nepavyko"
        }
    }

    fun loadRecentView(): RecentViewPreferences = runCatching {
        val json = JSONObject(preferences.getString(KEY_RECENT_VIEW, null) ?: return@runCatching RecentViewPreferences())
        RecentViewPreferences(
            sort = json.optString("sort").takeIf { it in setOf("RECENT", "NAME", "SIZE", "TYPE") } ?: "RECENT",
            ascending = json.optBoolean("ascending"),
            dateRange = json.optString("dateRange").takeIf { it in setOf("ALL", "TODAY", "LAST_7_DAYS", "LAST_30_DAYS") } ?: "ALL",
        )
    }.getOrDefault(RecentViewPreferences())

    fun saveRecentView(value: RecentViewPreferences) {
        val json = JSONObject()
            .put("sort", value.sort)
            .put("ascending", value.ascending)
            .put("dateRange", value.dateRange)
        check(preferences.edit().putString(KEY_RECENT_VIEW, json.toString()).commit()) {
            "Nustatymų įrašyti nepavyko"
        }
    }

    fun loadFeatureVisibility(): FeatureVisibility = runCatching {
        val json = JSONObject(preferences.getString(KEY_FEATURES, null) ?: return@runCatching FeatureVisibility())
        if (json.optInt("version") != 1) return@runCatching FeatureVisibility()
        val names = json.stringSet("hidden")
        FeatureVisibility(OptionalFeature.entries.filterTo(linkedSetOf()) { it.name in names })
    }.getOrDefault(FeatureVisibility())

    fun saveFeatureVisibility(value: FeatureVisibility) {
        val json = JSONObject().put("version", 1)
            .put("hidden", value.hidden.sortedBy { it.ordinal }.map { it.name }.toJsonArray())
        check(preferences.edit().putString(KEY_FEATURES, json.toString()).commit()) {
            "Nustatymų įrašyti nepavyko"
        }
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet {
            repeat(array.length()) { index ->
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun JSONObject.optionalInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun Collection<String>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach(array::put) }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValueOrNull<T>(value) ?: default

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private companion object {
        const val PREFS = "ui_preferences_v1"
        const val KEY_SEARCH = "search"
        const val KEY_RECENT_VIEW = "recent_view"
        const val KEY_FEATURES = "feature_visibility"
    }
}

internal object UiPreferenceRules {
    private const val MAX_PATH_LENGTH = 4_096
    private const val MAX_USERNAME_LENGTH = 128
    private const val MAX_RECEIVER_NAME_LENGTH = 80
    private const val MAX_SELECTED_ROOTS = 32
    private val supportedDayChoices = setOf(7, 30, 365)

    fun normalizeSearch(value: SearchDraftPreferences): SearchDraftPreferences = value.copy(
        selectedStoragePaths = value.selectedStoragePaths.asSequence()
            .map { cleanSingleLine(it, MAX_PATH_LENGTH) }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_SELECTED_ROOTS)
            .toCollection(linkedSetOf()),
        minimumMiB = decimal(value.minimumMiB),
        maximumMiB = decimal(value.maximumMiB),
        newerThanDays = value.newerThanDays?.takeIf(supportedDayChoices::contains),
        olderThanDays = value.olderThanDays?.takeIf(supportedDayChoices::contains),
    )

    private fun decimal(value: String): String {
        var decimalSeen = false
        return buildString {
            value.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    (character == '.' || character == ',') && !decimalSeen -> {
                        append('.')
                        decimalSeen = true
                    }
                }
            }
        }.take(16)
    }

    private fun cleanSingleLine(value: String, maximumLength: Int): String =
        value.filterNot(Char::isISOControl).trim().take(maximumLength)
}

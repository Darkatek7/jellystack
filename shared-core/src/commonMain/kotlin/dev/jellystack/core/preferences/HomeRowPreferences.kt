package dev.jellystack.core.preferences

/**
 * The user's choice of which Home rows show and in which order. Row ids are the ones the Home screen builds:
 * "continue", "next", "libraries", "my-list" and "plugin:<section id>". An empty [order] and no [hidden] rows
 * leave Home as built, which is the default.
 */
data class HomeRowPreferences(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

/** One Home row as the settings list shows it. */
data class HomeRowEntry(
    val id: String,
    val visible: Boolean,
)

/**
 * All rows of [availableIds] in the order the user chose, hidden ones included. Rows the user has not ordered yet,
 * such as a library added later, follow at the end in their available order and are visible.
 */
fun HomeRowPreferences.arrange(availableIds: List<String>): List<HomeRowEntry> {
    val available = availableIds.distinct()
    val ordered = order.filter { it in available }.distinct()
    val unordered = available.filter { it !in ordered }
    return (ordered + unordered).map { id -> HomeRowEntry(id, visible = id !in hidden) }
}

/** The ids of the rows Home shows, in order. */
fun HomeRowPreferences.visibleRowIds(availableIds: List<String>): List<String> = arrange(availableIds).filter { it.visible }.map { it.id }

/** Moves [id] by [offset] places among [availableIds]; the move stops at either end. */
fun HomeRowPreferences.move(
    availableIds: List<String>,
    id: String,
    offset: Int,
): HomeRowPreferences {
    val ids = arrange(availableIds).map { it.id }.toMutableList()
    val from = ids.indexOf(id)
    if (from < 0) return this
    val to = (from + offset).coerceIn(0, ids.lastIndex)
    if (to == from) return this
    ids.add(to, ids.removeAt(from))
    // Rows that are not available now (another Home layout) keep their place behind the visible ones.
    return copy(order = ids + order.filter { it !in ids })
}

/** Shows or hides [id]. */
fun HomeRowPreferences.withVisibility(
    id: String,
    visible: Boolean,
): HomeRowPreferences = copy(hidden = if (visible) hidden - id else hidden + id)

internal fun HomeRowPreferences.encodeOrder(): String = order.joinToString(HOME_ROW_SEPARATOR)

internal fun HomeRowPreferences.encodeHidden(): String = hidden.joinToString(HOME_ROW_SEPARATOR)

internal fun decodeHomeRowIds(raw: String?): List<String> = raw?.split(HOME_ROW_SEPARATOR)?.filter(String::isNotBlank).orEmpty()

private const val HOME_ROW_SEPARATOR = "\n"

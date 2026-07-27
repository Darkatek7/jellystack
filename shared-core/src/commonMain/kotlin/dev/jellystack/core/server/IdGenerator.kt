package dev.jellystack.core.server

import kotlin.random.Random

fun randomId(
    length: Int = 12,
    random: Random = Random,
): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(length) {
        repeat(length) {
            append(alphabet[random.nextInt(alphabet.length)])
        }
    }
}

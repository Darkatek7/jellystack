package dev.jellystack.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

suspend fun runTestBlocking(block: suspend TestScope.() -> Unit) {
    runTest { block() }
}

fun TestScope.toCoroutineScope(): CoroutineScope = this

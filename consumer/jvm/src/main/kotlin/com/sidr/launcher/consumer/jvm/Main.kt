package com.sidr.launcher.consumer.jvm

import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * The second consumer's entry point. Usage:
 *
 * ```
 * ./gradlew :consumer:jvm:run --args="<sandbox dir> remove <file name>"
 * ```
 *
 * Not a product and not a desktop application (Master Plan §3.1a, "не продукт и не десктопное
 * приложение, а консольный harness").
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("usage: <sandbox directory> <goal>   e.g.  /tmp/box remove stale.lock")
        return
    }
    val root = Paths.get(args.first()).toAbsolutePath()
    val goal = args.drop(1).joinToString(" ")

    val state = runBlocking {
        ConsoleHarness(root = root, out = ::println, ask = ::readlnOrNull).run(goal)
    }
    println("Final state: $state")
}

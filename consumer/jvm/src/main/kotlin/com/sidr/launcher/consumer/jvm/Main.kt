package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.bridge.JdkBridgeTransport
import com.sidr.launcher.consumer.jvm.bridge.JdkHttpSender
import com.sidr.launcher.domain.ai.bridge.BridgeBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * The second consumer's entry point. Usage:
 *
 * ```
 * ./gradlew :consumer:jvm:run --args="<sandbox dir> remove <file name>"
 * ./gradlew :consumer:jvm:run --args="<sandbox dir> ask <goal>"
 * ```
 *
 * `ask` sends the goal to the model (A4′ phase 3b, B-13) through `JdkBridgeTransport`, configured from the
 * environment only (controller ruling R13-3): `SIDR_JVM_BASE_URL` (an `https://` OpenAI-compatible base),
 * `SIDR_JVM_MODEL`, `SIDR_JVM_API_KEY`. With any of them absent nothing is sent, and the harness says the
 * model is unavailable and why. The key is never printed. This wiring is the one part of the model path no
 * test runs: no test reads the environment or touches the network.
 *
 * Not a product and not a desktop application (Master Plan §3.1a, "не продукт и не десктопное
 * приложение, а консольный harness").
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("usage: <sandbox directory> <goal>   e.g.  /tmp/box remove stale.lock   ·   /tmp/box ask <goal>")
        return
    }
    val root = Paths.get(args.first()).toAbsolutePath()
    val goal = args.drop(1).joinToString(" ")
    val transport = JdkBridgeTransport(
        baseUrl = System.getenv(JdkBridgeTransport.ENV_BASE_URL),
        model = System.getenv(JdkBridgeTransport.ENV_MODEL),
        key = System.getenv(JdkBridgeTransport.ENV_API_KEY),
        sender = JdkHttpSender(timeoutMs = BridgeBudget.Default.longestCeilingMs),
    )

    val state = runBlocking {
        ConsoleHarness(
            root = root,
            out = ::println,
            ask = ::readlnOrNull,
            worldContext = Dispatchers.IO,
            transport = transport,
        ).run(goal)
    }
    println("Final state: $state")
}

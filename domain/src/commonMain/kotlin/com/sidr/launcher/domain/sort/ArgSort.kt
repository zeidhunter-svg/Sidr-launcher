package com.sidr.launcher.domain.sort

/**
 * What a tool argument's words MEAN — an app, a duration, a clock time, an address, a file — beside
 * what they are on the wire (A4′ phase 3b, fork 3b-C; ADR «2026-11-07 — A4′ ф3b: сорт — отдельная ось;
 * F6 закрыт»).
 *
 * **Two axes, never one.** The type (`ArgType`) is the wire form and stays `STRING` for good; the sort
 * is meaning plus a [SortResolver] that turns the user's words into a value on the device. A sort is
 * declared **beside** `ToolDescriptor` by each consumer ([ArgumentSorts]), never inside it, so neither
 * `ToolDescriptor` nor `ArgType` moves when a sort is added.
 *
 * **An open value class over [String], not an enum** — the `ToolId` / `ToolLevel` reasoning: a new
 * sort is a new value and costs zero edits in other modules, where a closed sum would cost every
 * exhaustive `when` outside `:domain` (CLAUDE.md, «A closed sum in `commonMain` is not a one-file edit»).
 */
@JvmInline
value class ArgSort(val value: String)

/** The sorts this tree knows today. Android registers a resolver for the first four; [FILE] is the JVM sandbox's. */
object ArgSorts {
    val APP = ArgSort("app")
    val DURATION = ArgSort("duration")
    val CLOCK_TIME = ArgSort("clock_time")
    val URL = ArgSort("url")
    val FILE = ArgSort("file")
}

/**
 * One argument's declared sort. [labelArg] names the companion argument that receives the words the
 * user actually typed, when the tool declares one — so a surface can show what was said beside what it
 * resolved to; `null` when the tool has none (`open_app_info`: its step line then renders the resolved
 * value — A1″ residual (10), a legibility limit, named and deliberately not softened).
 */
data class SortedArg(val sort: ArgSort, val labelArg: String?)

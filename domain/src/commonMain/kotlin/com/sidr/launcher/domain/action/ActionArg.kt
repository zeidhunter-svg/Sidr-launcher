package com.sidr.launcher.domain.action

/**
 * The value type of an [ActionArg]. MVP is string-only — every current family takes a single free-text
 * argument (an app query, a search query, a URL). Kept as an enum (rather than assumed) so AIL-4's
 * strict arg validation has a type to check against, and so richer types (ENUM/INT/…) can be added
 * without breaking the [ActionDescriptor] contract.
 */
enum class ArgType {
    STRING,
}

/**
 * One named argument in an [ActionDescriptor.argSchema].
 *
 * Every tool declares its arguments with this type (`ToolDescriptor.argSchema`; an action family's
 * schema reaches it through the `in_app` projection, `SystemIntentToolSource`), and `InvocationValidator`
 * checks every step's arguments against it, an undeclared or a missing [required] argument failing
 * closed. The model never reads it — it reads our authored descriptions, kept in step with it by
 * `AuthoredToolDescriptionsTest`. The AIL-4 router that first rendered and strict-validated it was
 * deleted in A4′ phase 3a.
 *
 * @property name the argument key a tool invocation carries.
 * @property type the value type ([ArgType.STRING] for the MVP).
 * @property required whether the action cannot be built without it.
 * @property description short human-facing explanation of what the value is.
 */
data class ActionArg(
    val name: String,
    val type: ArgType = ArgType.STRING,
    val required: Boolean = true,
    val description: String,
)

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
 * The schema is what AIL-1 hands to two consumers: it renders to human/schema text for the LLM router
 * (AIL-4) and it is what the router's parsed JSON args are strict-validated against — an arg the model
 * emits that isn't declared here, or a missing [required] arg, fails the parse closed to `NoPlan`.
 *
 * @property name the argument key (matches the JSON key the router expects/emits).
 * @property type the value type ([ArgType.STRING] for the MVP).
 * @property required whether the action cannot be built without it.
 * @property description short human/LLM-facing explanation of what the value is.
 */
data class ActionArg(
    val name: String,
    val type: ArgType = ArgType.STRING,
    val required: Boolean = true,
    val description: String,
)

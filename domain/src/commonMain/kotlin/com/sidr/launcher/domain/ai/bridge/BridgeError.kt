package com.sidr.launcher.domain.ai.bridge

/**
 * Why an answer was rejected — the CLOSED vocabulary the one retry may carry back to the model (spec
 * §7.12.2). A retry sends the code's static note and nothing else: never the rejected answer, never
 * anything the device found while binding.
 */
enum class BridgeError { SHAPE, UNKNOWN_TOOL, NOT_OFFERED, UNDERIVABLE_MENTION }

package com.autoglm.phoneagent.action

/**
 * Represents an action that can be executed by the agent.
 * This maps to the action format returned by the AutoGLM model.
 */
data class AgentAction(
    val type: ActionType,
    val coordinates: IntArray = intArrayOf(),
    val start: IntArray? = null,
    val end: IntArray? = null,
    val text: String? = null,
    val message: String? = null,
    val duration: Long? = null,
    val rawAction: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AgentAction

        if (type != other.type) return false
        if (!coordinates.contentEquals(other.coordinates)) return false
        if (start != null) {
            if (other.start == null) return false
            if (!start.contentEquals(other.start)) return false
        } else if (other.start != null) return false
        if (end != null) {
            if (other.end == null) return false
            if (!end.contentEquals(other.end)) return false
        } else if (other.end != null) return false
        if (text != other.text) return false
        if (message != other.message) return false
        if (duration != other.duration) return false
        if (rawAction != other.rawAction) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + coordinates.contentHashCode()
        result = 31 * result + (start?.contentHashCode() ?: 0)
        result = 31 * result + (end?.contentHashCode() ?: 0)
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (duration?.hashCode() ?: 0)
        result = 31 * result + rawAction.hashCode()
        return result
    }

    companion object {
        /**
         * Parse action string from model response.
         * Expected formats:
         * - do(action="Tap", element=[x,y])
         * - do(action="Type", text="xxx")
         * - do(action="Swipe", start=[x1,y1], end=[x2,y2])
         * - do(action="Back")
         * - do(action="Wait", duration="x seconds")
         * - do(action="Take_over", message="xxx")
         * - finish(message="xxx")
         */
        fun parse(actionString: String): AgentAction {
            val trimmed = actionString.trim()

            return when {
                // Finish action
                trimmed.startsWith("finish(message=") -> {
                    val message = extractQuotedValue(trimmed, "finish(message=")
                    AgentAction(
                        type = ActionType.FINISH,
                        message = message,
                        rawAction = trimmed
                    )
                }

                // Tap action
                trimmed.contains("do(action=\"Tap\"") || trimmed.contains("do(action='Tap'") -> {
                    println("   Parsing Tap action: $trimmed")
                    val coords = extractCoordinates(trimmed)
                    println("   Extracted coords: ${coords.contentToString()}")
                    val message = extractQuotedValue(trimmed, "message=")
                    if (message != null) {
                        AgentAction(
                            type = ActionType.TAP_WITH_MESSAGE,
                            coordinates = coords,
                            message = message,
                            rawAction = trimmed
                        )
                    } else {
                        AgentAction(
                            type = ActionType.TAP,
                            coordinates = coords,
                            rawAction = trimmed
                        )
                    }
                }

                // Type action
                trimmed.contains("do(action=\"Type\"") || trimmed.contains("do(action='Type'") -> {
                    val text = extractQuotedValue(trimmed, "text=")
                    AgentAction(
                        type = ActionType.TYPE,
                        text = text ?: "",
                        rawAction = trimmed
                    )
                }

                // Swipe action
                trimmed.contains("do(action=\"Swipe\"") || trimmed.contains("do(action='Swipe'") -> {
                    val start = extractCoordinates(trimmed, "start=")
                    val end = extractCoordinates(trimmed, "end=")
                    AgentAction(
                        type = ActionType.SWIPE,
                        start = start,
                        end = end,
                        rawAction = trimmed
                    )
                }

                // Long Press action
                trimmed.contains("do(action=\"Long Press\"") || trimmed.contains("do(action='Long Press'") -> {
                    val coords = extractCoordinates(trimmed)
                    AgentAction(
                        type = ActionType.LONG_PRESS,
                        coordinates = coords,
                        rawAction = trimmed
                    )
                }

                // Double Tap action
                trimmed.contains("do(action=\"Double Tap\"") || trimmed.contains("do(action='Double Tap'") -> {
                    val coords = extractCoordinates(trimmed)
                    AgentAction(
                        type = ActionType.DOUBLE_TAP,
                        coordinates = coords,
                        rawAction = trimmed
                    )
                }

                // Back action
                trimmed.contains("do(action=\"Back\"") || trimmed.contains("do(action='Back'") -> {
                    AgentAction(
                        type = ActionType.BACK,
                        rawAction = trimmed
                    )
                }

                // Wait action
                trimmed.contains("do(action=\"Wait\"") || trimmed.contains("do(action='Wait'") -> {
                    val durationStr = extractQuotedValue(trimmed, "duration=")
                    val duration = parseDuration(durationStr ?: "1 seconds")
                    AgentAction(
                        type = ActionType.WAIT,
                        duration = duration,
                        rawAction = trimmed
                    )
                }

                // Take_over action
                trimmed.contains("do(action=\"Take_over\"") || trimmed.contains("do(action='Take_over'") -> {
                    val message = extractQuotedValue(trimmed, "message=")
                    AgentAction(
                        type = ActionType.TAKE_OVER,
                        message = message ?: "需要用户协助",
                        rawAction = trimmed
                    )
                }

                // Unknown action
                else -> {
                    AgentAction(
                        type = ActionType.UNKNOWN,
                        rawAction = trimmed
                    )
                }
            }
        }

        private fun extractCoordinates(actionString: String, key: String = "element="): IntArray {
            // Escape special regex characters in key to avoid parsing errors
            val escapedKey = Regex.escape(key)
            // Match both [x,y) and [x,y] formats
            val pattern = """$escapedKey\[(\d+),\s*(\d+)\]""".toRegex()
            val match = pattern.find(actionString)
            println("   extractCoordinates: key=$escapedKey, actionString=$actionString")
            println("   pattern=$pattern, match=$match")
            return if (match != null) {
                val x = match.groupValues[1].toInt()
                val y = match.groupValues[2].toInt()
                println("   parsed coordinates: [$x, $y]")
                intArrayOf(x, y)
            } else {
                println("   No match, using default [500, 500]")
                intArrayOf(500, 500) // Default center
            }
        }

        private fun extractQuotedValue(actionString: String, key: String): String? {
            // Escape special regex characters in key to avoid parsing errors
            // This fixes issues when key contains parentheses like "finish(message="
            val escapedKey = Regex.escape(key)
            val pattern = """$escapedKey["']([^"']+)["']""".toRegex()
            val match = pattern.find(actionString)
            return match?.groupValues?.get(1)
        }

        private fun parseDuration(durationStr: String): Long {
            val pattern = """(\d+)\s*(second|seconds|s)""".toRegex()
            val match = pattern.find(durationStr)
            return if (match != null) {
                match.groupValues[1].toLong() * 1000
            } else {
                1000L // Default 1 second
            }
        }
    }
}

/**
 * Enum representing all possible action types that the agent can execute.
 */
enum class ActionType {
    // Tap action - click at coordinates
    TAP,
    // Tap with confirmation message - for sensitive actions
    TAP_WITH_MESSAGE,
    // Type action - input text
    TYPE,
    // Swipe action - drag from start to end
    SWIPE,
    // Long press action - long press at coordinates
    LONG_PRESS,
    // Double tap action - double tap at coordinates
    DOUBLE_TAP,
    // Back action - navigate back
    BACK,
    // Wait action - wait for specified duration
    WAIT,
    // Take over action - request user assistance
    TAKE_OVER,
    // Finish action - task completed
    FINISH,
    // Unknown action - could not parse
    UNKNOWN
}

package com.autoglm.phoneagent.agent

/**
 * Configuration for the AgentEngine.
 *
 * @property maxSteps Maximum number of steps to execute before stopping
 * @property stepDelay Delay between steps in milliseconds to allow UI to update
 * @property verbose Whether to log detailed information
 * @property autoConfirmSensitiveActions Whether to auto-confirm sensitive actions (default: false)
 * @property unknownActionThreshold Number of consecutive unknown actions before stopping (default: 3)
 */
data class AgentConfig(
    val maxSteps: Int = 50,
    val stepDelay: Long = 500,
    val verbose: Boolean = true,
    val autoConfirmSensitiveActions: Boolean = false,
    val unknownActionThreshold: Int = 3
) {
    companion object {
        /**
         * Default configuration with all default values.
         */
        val DEFAULT = AgentConfig()

        /**
         * Fast configuration with reduced delays and fewer steps for quick testing.
         */
        val FAST = AgentConfig(
            maxSteps = 20,
            stepDelay = 300,
            verbose = false
        )

        /**
         * Debug configuration with verbose logging and longer delays.
         */
        val DEBUG = AgentConfig(
            maxSteps = 100,
            stepDelay = 1000,
            verbose = true
        )

        /**
         * Production configuration optimized for production use.
         */
        val PRODUCTION = AgentConfig(
            maxSteps = 50,
            stepDelay = 500,
            verbose = false,
            autoConfirmSensitiveActions = false
        )
    }

    /**
     * Validate the configuration.
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        require(maxSteps > 0) { "maxSteps must be greater than 0" }
        require(stepDelay >= 0) { "stepDelay must be non-negative" }
        require(unknownActionThreshold > 0) { "unknownActionThreshold must be greater than 0" }
    }
}

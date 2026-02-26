package com.demo.phoneagent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.autoglm.phoneagent.agent.AgentResult

/**
 * ViewModel for MainActivity.
 * Holds the UI state for the agent execution.
 */
class MainViewModel : ViewModel() {

    private val _isRunning = MutableLiveData<Boolean>(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _currentStep = MutableLiveData<Int>(0)
    val currentStep: LiveData<Int> = _currentStep

    private val _currentThinking = MutableLiveData<String>("")
    val currentThinking: LiveData<String> = _currentThinking

    private val _currentAction = MutableLiveData<String>("")
    val currentAction: LiveData<String> = _currentAction

    private val _executionResult = MutableLiveData<AgentResult?>()
    val executionResult: LiveData<AgentResult?> = _executionResult

    /**
     * Update the current step info.
     */
    fun updateStep(step: Int, thinking: String, action: String) {
        _currentStep.postValue(step)
        _currentThinking.postValue(thinking)
        _currentAction.postValue(action)
    }

    /**
     * Set the execution result.
     */
    fun setResult(result: AgentResult) {
        _isRunning.postValue(false)
        _executionResult.postValue(result)
    }

    /**
     * Set running state.
     */
    fun setRunning(running: Boolean) {
        _isRunning.postValue(running)
    }

    /**
     * Reset the state.
     */
    fun reset() {
        _isRunning.value = false
        _currentStep.value = 0
        _currentThinking.value = ""
        _currentAction.value = ""
        _executionResult.value = null
    }
}

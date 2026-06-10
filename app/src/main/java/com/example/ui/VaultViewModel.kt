package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface VaultUiState {
    object Loading : VaultUiState
    object Setup : VaultUiState // First time setup wizard
    data class PasswordLock(val stealthMode: Boolean) : VaultUiState
    object DecoyUnlocked : VaultUiState
    object MainUnlocked : VaultUiState
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VaultDatabase.getDatabase(application)
    private val repository = VaultRepository(application, db.vaultDao())

    // UI States
    private val _isDecoyUnlocked = MutableStateFlow(false)
    private val _isMainUnlocked = MutableStateFlow(false)
    private val _hasPinSet = MutableStateFlow<Boolean?>(null)
    private val _stealthMode = MutableStateFlow(true)
    private val _breakInLogs = repository.breakInLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current lists of media
    private val _mainItems = repository.getVaultItems(isDecoy = false).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _decoyItems = repository.getVaultItems(isDecoy = true).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Failed attempt tracker
    private val _failedAttemptsCount = MutableStateFlow(0)

    // Calculator Specific State
    val calcExpression = MutableStateFlow("")
    val calcResult = MutableStateFlow("")

    val uiState: StateFlow<VaultUiState> = combine(
        _hasPinSet,
        _isMainUnlocked,
        _isDecoyUnlocked,
        _stealthMode
    ) { hasPin, mainUn, decoyUn, stealth ->
        when (hasPin) {
            null -> VaultUiState.Loading
            false -> VaultUiState.Setup
            true -> {
                when {
                    mainUn -> VaultUiState.MainUnlocked
                    decoyUn -> VaultUiState.DecoyUnlocked
                    else -> VaultUiState.PasswordLock(stealth)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState.Loading
    )

    val logs: StateFlow<List<BreakInLog>> = _breakInLogs
    val isStealthActive: StateFlow<Boolean> = _stealthMode
    val failedAttempts: StateFlow<Int> = _failedAttemptsCount
    val mainItems: StateFlow<List<VaultItem>> = _mainItems
    val decoyItems: StateFlow<List<VaultItem>> = _decoyItems

    init {
        checkInitialSetup()
    }

    private fun checkInitialSetup() {
        viewModelScope.launch {
            val pin = repository.getPin()
            _stealthMode.value = repository.isStealthMode()
            _hasPinSet.value = !pin.isNullOrEmpty()
        }
    }

    /**
     * Completes the first time installation setup
     */
    fun completeSetup(pin: String, decoyPin: String?, useCalculatorSkin: Boolean) {
        viewModelScope.launch {
            if (pin.isNotEmpty()) {
                repository.setPin(pin)
                if (!decoyPin.isNullOrEmpty()) {
                    repository.setDecoyPin(decoyPin)
                }
                repository.setStealthMode(useCalculatorSkin)
                _stealthMode.value = useCalculatorSkin
                _hasPinSet.value = true
                // Auto logon after setup
                _isMainUnlocked.value = true
            }
        }
    }

    /**
     * Authenticate PIN entered through Calculator or Pin Pad
     */
    fun authenticatePIN(pin: String): Boolean {
        var success = false
        viewModelScope.launch {
            val correctPin = repository.getPin()
            val correctDecoyPin = repository.getDecoyPin()

            when {
                pin == correctPin -> {
                    _isMainUnlocked.value = true
                    _isDecoyUnlocked.value = false
                    _failedAttemptsCount.value = 0
                    success = true
                }
                !correctDecoyPin.isNullOrEmpty() && pin == correctDecoyPin -> {
                    _isDecoyUnlocked.value = true
                    _isMainUnlocked.value = false
                    _failedAttemptsCount.value = 0
                    success = true
                }
                else -> {
                    // Logic failed attempt
                    _failedAttemptsCount.value += 1
                    repository.logBreakInAttempt(pin)
                }
            }
        }
        return success
    }

    /**
     * Force locks vault immediately
     */
    fun lockVault() {
        _isMainUnlocked.value = false
        _isDecoyUnlocked.value = false
        calcExpression.value = ""
        calcResult.value = ""
    }

    /**
     * Import gallery selection and associate with main or decoy vault space
     */
    fun importSelectedMedia(uris: List<Uri>, isDecoy: Boolean, onComplete: (successCount: Int, failedUris: List<Uri>) -> Unit) {
        viewModelScope.launch {
            var successCount = 0
            val failedUris = mutableListOf<Uri>()

            for (uri in uris) {
                val item = repository.importMedia(uri, isDecoy)
                if (item != null) {
                    successCount++
                } else {
                    failedUris.add(uri)
                }
            }
            onComplete(successCount, failedUris)
        }
    }

    /**
     * Import a photograph taken right inside the application camera
     */
    fun importCameraCapture(bitmap: Bitmap, isDecoy: Boolean) {
        viewModelScope.launch {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            val bytes = outputStream.toByteArray()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val name = "CAP_$timestamp.jpg"

            repository.importCapturedBytes(bytes, name, isDecoy)
        }
    }

    /**
     * Delete media item from the vault (and safely deletes internal file copy)
     */
    fun deleteMediaItem(item: VaultItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    /**
     * Change Stealth Theme preference
     */
    fun toggleStealthMode() {
        viewModelScope.launch {
            val nextState = !_stealthMode.value
            repository.setStealthMode(nextState)
            _stealthMode.value = nextState
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearBreakInLogs()
        }
    }

    // --- Calculator Engine Logic ---
    fun onCalcBtnClick(char: String) {
        val currentExp = calcExpression.value

        when (char) {
            "C" -> {
                calcExpression.value = ""
                calcResult.value = ""
            }
            "DEL" -> {
                if (currentExp.isNotEmpty()) {
                    calcExpression.value = currentExp.substring(0, currentExp.length - 1)
                }
            }
            "=" -> {
                // Secret trigger pattern check
                if (authenticatePIN(currentExp)) {
                    calcExpression.value = ""
                    calcResult.value = ""
                } else {
                    // Treat as basic math evaluator
                    evaluateExpression(currentExp)
                }
            }
            else -> {
                calcExpression.value = currentExp + char
            }
        }
    }

    private fun evaluateExpression(expr: String) {
        try {
            // Support basic clean expressions (e.g., 12.5+4*3) without using heavy scripting engines
            if (expr.isEmpty()) return
            val sanitized = expr.replace("×", "*").replace("÷", "/")
            val result = simpleMathEvaluate(sanitized)
            calcResult.value = result
        } catch (e: Exception) {
            calcResult.value = "שגיאה"
        }
    }

    private fun simpleMathEvaluate(expression: String): String {
        // Fast fallback: if we only typed digits, echo it back
        if (expression.all { it.isDigit() }) {
            return expression
        }

        // Extremely simple parser for display calculations
        val operators = listOf('+', '-', '*', '/')
        var operatorIndex = -1
        var activeOperator = ' '

        for (op in operators) {
            val idx = expression.lastIndexOf(op)
            if (idx > 0) {
                operatorIndex = idx
                activeOperator = op
                break
            }
        }

        if (operatorIndex == -1) return expression

        val firstPart = expression.substring(0, operatorIndex).trim().toDoubleOrNull() ?: 0.0
        val secondPart = expression.substring(operatorIndex + 1).trim().toDoubleOrNull() ?: 0.0

        val result = when (activeOperator) {
            '+' -> firstPart + secondPart
            '-' -> firstPart - secondPart
            '*' -> firstPart * secondPart
            '/' -> if (secondPart != 0.0) firstPart / secondPart else Double.NaN
            else -> 0.0
        }

        return if (result.isNaN()) "שגיאה" else {
            if (result % 1.0 == 0.0) result.toInt().toString() else "%.2f".format(result)
        }
    }
}

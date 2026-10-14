package com.pingpaisa.app.finance

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Financial profile in EncryptedSharedPreferences (AES-256, key in Android Keystore). If the Keystore is
 * unusable the profile is kept in memory only (never written in plain text) and [persistent] is false.
 */
class FinancialProfileStore(private val context: Context) {
    private val prefs: SharedPreferences? = open()
    val persistent: Boolean get() = prefs != null

    private val _profile = MutableStateFlow(read())
    /** null = the user has never saved a profile. */
    val profile: StateFlow<FinancialProfile?> = _profile.asStateFlow()

    fun save(p: FinancialProfile) {
        _profile.value = p
        val e = prefs?.edit() ?: return
        e.putString("netIncome", p.netIncome.toString())
        e.putString("extraIncome", p.extraIncome.toString())
        e.putString("savings", p.savings.toString())
        e.putString("emergencyReserve", p.emergencyReserve.toString())
        e.putString("fixedExpenses", p.fixedExpenses.toString())
        e.putString("variableExpenses", p.variableExpenses.toString())
        e.putString("monthlySavingsTarget", p.monthlySavingsTarget.toString())
        e.putString("extraDebtEmi", p.extraDebtEmi.toString())
        e.putString("alpha", p.discretionaryShare.toString())
        e.putString("beta", p.savingsSpendShare.toString())
        e.putInt("maxWait", p.maxWaitMonths)
        e.putString("risk", p.risk.name)
        e.putBoolean("saved", true)
        e.apply()
    }

    fun clear() {
        _profile.value = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun read(): FinancialProfile? {
        val p = prefs ?: return null
        if (!p.getBoolean("saved", false)) return null
        fun d(k: String) = p.getString(k, null)?.toDoubleOrNull() ?: 0.0
        return FinancialProfile(
            netIncome = d("netIncome"), extraIncome = d("extraIncome"), savings = d("savings"),
            emergencyReserve = d("emergencyReserve"), fixedExpenses = d("fixedExpenses"),
            variableExpenses = d("variableExpenses"), monthlySavingsTarget = d("monthlySavingsTarget"),
            extraDebtEmi = d("extraDebtEmi"),
            discretionaryShare = p.getString("alpha", null)?.toDoubleOrNull() ?: RiskTolerance.BALANCED.alpha,
            savingsSpendShare = p.getString("beta", null)?.toDoubleOrNull() ?: RiskTolerance.BALANCED.beta,
            maxWaitMonths = p.getInt("maxWait", RiskTolerance.BALANCED.maxWaitMonths),
            risk = RiskTolerance.values().firstOrNull { it.name == p.getString("risk", null) } ?: RiskTolerance.BALANCED
        )
    }

    private fun open(): SharedPreferences? {
        repeat(2) { attempt ->
            try {
                val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                return EncryptedSharedPreferences.create(
                    context, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // a corrupted keystore/prefs pair is the usual cause: wipe once and retry
                if (attempt == 0) context.deleteSharedPreferences(FILE)
            }
        }
        return null
    }

    private companion object { const val FILE = "pp_finance_secure" }
}

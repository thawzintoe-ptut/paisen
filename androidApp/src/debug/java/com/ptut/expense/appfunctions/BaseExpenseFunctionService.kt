package com.ptut.expense.appfunctions

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionStringValueConstraint
import com.ptut.expense.MainActivity
import com.ptut.expense.receiveAddExpense
import com.ptut.expense.receiveAddSharedExpense
import com.ptut.expense.spentIn

/**
 * #213: what Gemini can ask of PaiSeN on Android 16 and later, the same three things Siri can.
 * Each argument is words, so composeApp's `AgentFunctions.kt` reads it as `AgentIntents.kt` reads
 * Siri's. The two that add only fill a sheet; the person still taps Save.
 *
 * Debug builds only, until Google's Early Access Program takes the app. KSP generates
 * `ExpenseFunctionService` from this class and `expense_functions.xml` from the KDoc below, which
 * is what Gemini reads, so it stays in English.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "ExpenseFunctionService",
    appFunctionXmlFileName = "expense_functions",
)
abstract class BaseExpenseFunctionService : AppFunctionService() {

    /**
     * Fills in a new expense in PaiSeN for the person to check. Nothing is saved until they tap
     * Save in the app.
     *
     * @param amount The amount in Thai baht, as said, for example "60" or "1,250.50".
     * @param category The spending category in the person's words, for example "coffee" or "taxi".
     * @param note A short note that names the expense, for example "lunch with Mai".
     * @return The filled expense sheet to open.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addTransaction(amount: String? = null, category: String? = null, note: String? = null): SheetToOpen {
        receiveAddExpense(amount, category, note)
        return SheetToOpen(intentToOpen = openApp())
    }

    /**
     * Says how much the person spent. Read only: nothing in the app changes.
     *
     * @param period "today", "week" for this week from Monday, or "month" for this month so far.
     * @return The amount spent in that period.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun spendSummary(
        @AppFunctionStringValueConstraint(enumValues = ["today", "week", "month"])
        period: String,
    ): SpendSummary = SpendSummary(spent = spentIn(period), period = period)

    /**
     * Fills in a new shared expense in one of the person's PaiSeN groups for them to check.
     * Nothing is saved until they tap Save in the app.
     *
     * @param group The group's name as said, for example "Chiang Mai trip".
     * @param amount The amount in Thai baht, as said, for example "450".
     * @param description What the money was for, for example "dinner".
     * @return The filled shared-expense sheet to open.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addGroupExpense(group: String? = null, amount: String? = null, description: String? = null): SheetToOpen {
        receiveAddSharedExpense(group, amount, description)
        return SheetToOpen(intentToOpen = openApp())
    }

    /** The shell opens whatever `AgentInbox` holds once it has drawn, as it does for Siri. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        OPEN_SHEET_REQUEST,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val OPEN_SHEET_REQUEST = 213
    }
}

/** A sheet in PaiSeN, filled in and waiting for the person to check it and tap Save. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SheetToOpen(
    /** Opens PaiSeN on the filled sheet. */
    val intentToOpen: PendingIntent,
)

/** What the person spent in one period. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SpendSummary(
    /** The total spent, as the app shows it, for example "฿1,250". */
    val spent: String,
    /** The period it covers: "today", "week" or "month". */
    val period: String,
)

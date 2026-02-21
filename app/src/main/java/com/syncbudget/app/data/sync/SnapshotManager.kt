package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.AmortizationRepository
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.CategoryRepository
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.IncomeSourceRepository
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.RecurringExpenseRepository
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SavingsGoalRepository
import com.syncbudget.app.data.Transaction
import com.syncbudget.app.data.TransactionRepository
import android.content.Context
import org.json.JSONObject

data class FullState(
    val transactions: List<Transaction>,
    val recurringExpenses: List<RecurringExpense>,
    val incomeSources: List<IncomeSource>,
    val savingsGoals: List<SavingsGoal>,
    val amortizationEntries: List<AmortizationEntry>,
    val categories: List<Category>
)

object SnapshotManager {

    fun serializeFullState(
        context: Context,
        transactions: List<Transaction>,
        recurringExpenses: List<RecurringExpense>,
        incomeSources: List<IncomeSource>,
        savingsGoals: List<SavingsGoal>,
        amortizationEntries: List<AmortizationEntry>,
        categories: List<Category>
    ): JSONObject {
        // Save each list to its repository format, then collect the JSON files
        // We leverage the existing repository serialization by temporarily saving and reading
        val json = JSONObject()

        // Serialize using repository patterns inline
        val txnArray = org.json.JSONArray()
        TransactionRepository.save(context, transactions)
        val txnFile = context.getFileStreamPath("transactions.json")
        if (txnFile.exists()) {
            json.put("transactions", org.json.JSONArray(txnFile.readText()))
        }

        RecurringExpenseRepository.save(context, recurringExpenses)
        val reFile = context.getFileStreamPath("recurring_expenses.json")
        if (reFile.exists()) {
            json.put("recurringExpenses", org.json.JSONArray(reFile.readText()))
        }

        IncomeSourceRepository.save(context, incomeSources)
        val isFile = context.getFileStreamPath("income_sources.json")
        if (isFile.exists()) {
            json.put("incomeSources", org.json.JSONArray(isFile.readText()))
        }

        SavingsGoalRepository.save(context, savingsGoals)
        val sgFile = context.getFileStreamPath("future_expenditures.json")
        if (sgFile.exists()) {
            json.put("savingsGoals", org.json.JSONArray(sgFile.readText()))
        }

        AmortizationRepository.save(context, amortizationEntries)
        val amFile = context.getFileStreamPath("amortization_entries.json")
        if (amFile.exists()) {
            json.put("amortizationEntries", org.json.JSONArray(amFile.readText()))
        }

        CategoryRepository.save(context, categories)
        val catFile = context.getFileStreamPath("categories.json")
        if (catFile.exists()) {
            json.put("categories", org.json.JSONArray(catFile.readText()))
        }

        return json
    }

    fun deserializeFullState(context: Context, json: JSONObject): FullState {
        // Write each array to its file, then use repository load
        if (json.has("transactions")) {
            context.openFileOutput("transactions.json", Context.MODE_PRIVATE).use {
                it.write(json.getJSONArray("transactions").toString().toByteArray())
            }
        }
        if (json.has("recurringExpenses")) {
            context.openFileOutput("recurring_expenses.json", Context.MODE_PRIVATE).use {
                it.write(json.getJSONArray("recurringExpenses").toString().toByteArray())
            }
        }
        if (json.has("incomeSources")) {
            context.openFileOutput("income_sources.json", Context.MODE_PRIVATE).use {
                it.write(json.getJSONArray("incomeSources").toString().toByteArray())
            }
        }
        if (json.has("savingsGoals")) {
            context.openFileOutput("future_expenditures.json", Context.MODE_PRIVATE).use {
                it.write(json.getJSONArray("savingsGoals").toString().toByteArray())
            }
        }
        if (json.has("amortizationEntries")) {
            context.openFileOutput("amortization_entries.json", Context.MODE_PRIVATE).use {
                it.write(json.getJSONArray("amortizationEntries").toString().toByteArray())
            }
        }
        if (json.has("categories")) {
            context.openFileOutput("categories.json", Context.MODE_PRIVATE).use {
                it.write(json.getJSONArray("categories").toString().toByteArray())
            }
        }

        return FullState(
            transactions = TransactionRepository.load(context),
            recurringExpenses = RecurringExpenseRepository.load(context),
            incomeSources = IncomeSourceRepository.load(context),
            savingsGoals = SavingsGoalRepository.load(context),
            amortizationEntries = AmortizationRepository.load(context),
            categories = CategoryRepository.load(context)
        )
    }
}

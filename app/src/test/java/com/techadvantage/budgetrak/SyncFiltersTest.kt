package com.techadvantage.budgetrak

import com.techadvantage.budgetrak.data.*
import com.techadvantage.budgetrak.data.sync.active
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SyncFiltersTest {

    private val today = LocalDate.of(2026, 1, 15)

    @Test
    fun transactions_active_filtersDeleted() {
        val list = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "A", amount = 10.0, deleted = false),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "B", amount = 20.0, deleted = true),
            Transaction(id = 3, type = TransactionType.EXPENSE, date = today,
                source = "C", amount = 30.0, deleted = false)
        )
        val active = list.active
        assertEquals(2, active.size)
        assertTrue(active.all { !it.deleted })
    }

    @Test
    fun recurringExpenses_active_filtersDeleted() {
        val list = listOf(
            RecurringExpense(id = 1, source = "A", amount = 10.0, deleted = true),
            RecurringExpense(id = 2, source = "B", amount = 20.0, deleted = false)
        )
        assertEquals(1, list.active.size)
        assertEquals(2, list.active[0].id)
    }

    @Test
    fun categories_active_filtersDeleted() {
        val list = listOf(
            Category(id = 1, name = "Food", iconName = "Icon", deleted = false),
            Category(id = 2, name = "Transport", iconName = "Icon", deleted = true)
        )
        assertEquals(1, list.active.size)
        assertEquals("Food", list.active[0].name)
    }

    @Test
    fun emptyList_active_returnsEmpty() {
        val list = emptyList<Transaction>()
        assertTrue(list.active.isEmpty())
    }

    @Test
    fun allDeleted_active_returnsEmpty() {
        val list = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "A", amount = 10.0, deleted = true),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "B", amount = 20.0, deleted = true)
        )
        assertTrue(list.active.isEmpty())
    }

    @Test
    fun noneDeleted_active_returnsSameList() {
        val list = listOf(
            Transaction(id = 1, type = TransactionType.EXPENSE, date = today,
                source = "A", amount = 10.0, deleted = false),
            Transaction(id = 2, type = TransactionType.EXPENSE, date = today,
                source = "B", amount = 20.0, deleted = false)
        )
        assertEquals(2, list.active.size)
    }
}

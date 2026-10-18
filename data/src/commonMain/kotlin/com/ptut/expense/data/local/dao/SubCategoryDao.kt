package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.SubCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubCategoryDao {

    @Query("SELECT * FROM sub_category ORDER BY name")
    fun observeAll(): Flow<List<SubCategoryEntity>>

    @Upsert
    suspend fun upsert(subCategory: SubCategoryEntity)

    /**
     * The row goes; the spending does not. `transactions.subCategoryId` is SET_NULL against this
     * table, so every transaction filed under it keeps its amount and its category and simply
     * stops being counted separately — which is what the screen promises before the tap.
     */
    @Query("DELETE FROM sub_category WHERE id = :id")
    suspend fun delete(id: String)
}

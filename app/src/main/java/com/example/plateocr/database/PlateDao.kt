// PlateDao.kt
package com.example.plateocr.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlateDao {
    @Insert
    suspend fun insert(plate: Plate)

    @Query("SELECT * FROM plates ORDER BY id DESC")
    suspend fun getAllPlates(): List<Plate>

    @Query("SELECT * FROM plates WHERE number = :number LIMIT 1")
    suspend fun getPlateByNumber(number: String): Plate?
}
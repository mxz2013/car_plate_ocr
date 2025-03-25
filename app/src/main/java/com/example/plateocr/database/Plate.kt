// Plate.kt
package com.example.plateocr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plates")
data class Plate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val number: String,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)
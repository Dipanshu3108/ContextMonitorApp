package com.example.contextmonitorapp

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "health_data")
data class HealthData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val heartRate: Float,
    val respiratoryRate: Float,
    val nausea: Int,
    val headache: Int,
    val diarrhea: Int,
    val soreThroat: Int,
    val fever: Int,
    val cough: Int,
    val shortnessOfBreath: Int,
    val muscleAche: Int,
    val feelingTired: Int,
    val lossOfSmellAndTaste: Int
)

package com.example.contextmonitorapp

//import HealthData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HealthDataDao {
    @Insert
    suspend fun insertHealthData(healthData: HealthData)

    @Query("SELECT * FROM health_data")
    suspend fun getAllHealthData(): List<HealthData>
}

package com.example.contextmonitorapp



import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.contextmonitorapp.databinding.ActivitySymptomsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SymptomsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySymptomsBinding
    private lateinit var database: AppDatabase
    private var heartRate: Float = 0f
    private var respiratoryRate: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySymptomsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the database instance
        database = AppDatabase.getDatabase(this)

        // Retrieve heart rate and respiratory rate passed from the previous activity
        heartRate = intent.getFloatExtra("HEART_RATE", 0f)
        respiratoryRate = intent.getFloatExtra("RESPIRATORY_RATE", 0f)

        // Set up the upload button
        binding.buttonUploadSymptoms.setOnClickListener {
            uploadSymptoms()
        }
    }

    private fun uploadSymptoms() {
        // Gather ratings from each symptom
        val nauseaRating = binding.ratingNausea.rating.toInt()
        val headacheRating = binding.ratingHeadache.rating.toInt()
        val diarrheaRating = binding.ratingDiarrhea.rating.toInt()
        val soreThroatRating = binding.ratingSoreThroat.rating.toInt()
        val feverRating = binding.ratingFever.rating.toInt()
        val coughRating = binding.ratingCough.rating.toInt()
        val muscleAcheRating = binding.ratingMuscleAche.rating.toInt()
        val feelingTiredRating = binding.ratingFeelingTired.rating.toInt()
        val lossTasteSmellRating = binding.ratingLossSmellTaste.rating.toInt()
        val shortnessOfBreathRating = binding.shortnessOfBreath.rating.toInt()

        // Create a new HealthData object to store in Room
        val healthData = HealthData(
            heartRate = heartRate,
            respiratoryRate = respiratoryRate,
            nausea = nauseaRating,
            headache = headacheRating,
            diarrhea = diarrheaRating,
            soreThroat = soreThroatRating,
            fever = feverRating,
            cough = coughRating,
            muscleAche = muscleAcheRating,
            feelingTired = feelingTiredRating,
            shortnessOfBreath = shortnessOfBreathRating,
            lossOfSmellAndTaste = lossTasteSmellRating
        )

        // Insert the data into the database using Room
        CoroutineScope(Dispatchers.IO).launch {
            database.healthDataDao().insertHealthData(healthData)

            // Navigate back to the main page on successful upload
            runOnUiThread {
                Toast.makeText(this@SymptomsActivity, "Symptoms uploaded successfully!", Toast.LENGTH_SHORT).show()

                // Navigate back to MainActivity
                val intent = Intent(this@SymptomsActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
        }
    }
}

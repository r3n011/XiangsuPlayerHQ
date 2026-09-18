package com.theveloper.pixelplay.data.autoeq

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for loading and accessing AutoEQ profiles
 */
@Singleton
class AutoEQManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var database: AutoEQDatabase? = null
    private val gson = Gson()

    /**
     * Load AutoEQ profiles from assets
     */
    suspend fun loadProfiles(): Result<AutoEQDatabase> = withContext(Dispatchers.IO) {
        try {
            if (database != null) {
                return@withContext Result.success(database!!)
            }

            val jsonString = context.assets.open("autoeq_profiles.json").use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }

            val loadedDatabase = gson.fromJson(jsonString, AutoEQDatabase::class.java)
            database = loadedDatabase
            Result.success(loadedDatabase)

        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all available profiles
     */
    fun getAllProfiles(): List<AutoEQProfile> {
        return database?.profiles ?: emptyList()
    }

    /**
     * Search profiles by name or brand
     */
    fun searchProfiles(query: String): List<AutoEQProfile> {
        return database?.searchProfiles(query) ?: emptyList()
    }

    /**
     * Get profiles by brand
     */
    fun getProfilesByBrand(brand: String): List<AutoEQProfile> {
        return database?.getProfilesByBrand(brand) ?: emptyList()
    }

    /**
     * Get profiles by type (Over-Ear, In-Ear, On-Ear)
     */
    fun getProfilesByType(type: String): List<AutoEQProfile> {
        return database?.getProfilesByType(type) ?: emptyList()
    }

    /**
     * Get all available brands
     */
    fun getAllBrands(): List<String> {
        return database?.getAllBrands() ?: emptyList()
    }

    /**
     * Get all available types
     */
    fun getAllTypes(): List<String> {
        return database?.getAllTypes() ?: emptyList()
    }

    /**
     * Find a profile by exact name match
     */
    fun findProfileByName(name: String): AutoEQProfile? {
        return database?.profiles?.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Get recommended profiles (top popular models)
     */
    fun getRecommendedProfiles(): List<AutoEQProfile> {
        val recommended = listOf(
            "Sony WH-1000XM4",
            "AirPods Pro",
            "Sennheiser HD 600",
            "Bose QuietComfort 45",
            "Samsung Galaxy Buds Pro"
        )

        return database?.profiles?.filter { it.name in recommended } ?: emptyList()
    }
}

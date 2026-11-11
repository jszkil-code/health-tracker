package dev.healthtracker.app.core.units

import java.util.Locale
import kotlin.math.roundToLong

/** Display unit system. Storage is always metric (kg, g, kcal, ms); this only affects display. */
enum class UnitSystem { US, METRIC }

object UnitConverter {
    const val LB_PER_KG = 2.2046226218
    const val G_PER_OZ = 28.349523125

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG
    fun lbToKg(lb: Double): Double = lb / LB_PER_KG
    fun gToOz(g: Double): Double = g / G_PER_OZ
    fun ozToG(oz: Double): Double = oz * G_PER_OZ

    /** Formats a stored body weight (kg) for display, one decimal place. */
    fun formatBodyWeight(kg: Double, system: UnitSystem): String = when (system) {
        UnitSystem.US -> "${oneDecimal(kgToLb(kg))} lb"
        UnitSystem.METRIC -> "${oneDecimal(kg)} kg"
    }

    /** Formats a stored food weight (g) for display. */
    fun formatFoodWeight(g: Double, system: UnitSystem): String = when (system) {
        UnitSystem.US -> "${oneDecimal(gToOz(g))} oz"
        UnitSystem.METRIC -> "${g.roundToLong()} g"
    }

    /** Converts a user-entered body weight back to kg for storage. */
    fun bodyWeightToKg(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.US -> lbToKg(value)
        UnitSystem.METRIC -> value
    }

    /** Converts a user-entered food weight back to grams for storage. */
    fun foodWeightToG(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.US -> ozToG(value)
        UnitSystem.METRIC -> value
    }

    private fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)
}

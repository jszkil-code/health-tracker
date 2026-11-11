package dev.healthtracker.app.core.units

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test fun kgToLb() = assertEquals(220.462, UnitConverter.kgToLb(100.0), 0.001)
    @Test fun lbToKg() = assertEquals(45.359, UnitConverter.lbToKg(100.0), 0.001)
    @Test fun ozToG() = assertEquals(28.3495, UnitConverter.ozToG(1.0), 0.0001)
    @Test fun gToOz() = assertEquals(3.5274, UnitConverter.gToOz(100.0), 0.0001)

    @Test fun roundTripsAreLossless() {
        assertEquals(81.3, UnitConverter.lbToKg(UnitConverter.kgToLb(81.3)), 1e-9)
        assertEquals(250.0, UnitConverter.ozToG(UnitConverter.gToOz(250.0)), 1e-9)
    }

    @Test fun formatsBodyWeight() {
        assertEquals("178.6 lb", UnitConverter.formatBodyWeight(81.0, UnitSystem.US))
        assertEquals("81.0 kg", UnitConverter.formatBodyWeight(81.0, UnitSystem.METRIC))
    }

    @Test fun formatsFoodWeight() {
        assertEquals("5.3 oz", UnitConverter.formatFoodWeight(150.0, UnitSystem.US))
        assertEquals("150 g", UnitConverter.formatFoodWeight(150.4, UnitSystem.METRIC))
    }

    @Test fun userInputConvertsToMetricForStorage() {
        assertEquals(81.0, UnitConverter.bodyWeightToKg(UnitConverter.kgToLb(81.0), UnitSystem.US), 1e-9)
        assertEquals(81.0, UnitConverter.bodyWeightToKg(81.0, UnitSystem.METRIC), 0.0)
        assertEquals(28.349523125, UnitConverter.foodWeightToG(1.0, UnitSystem.US), 1e-9)
        assertEquals(42.0, UnitConverter.foodWeightToG(42.0, UnitSystem.METRIC), 0.0)
    }
}

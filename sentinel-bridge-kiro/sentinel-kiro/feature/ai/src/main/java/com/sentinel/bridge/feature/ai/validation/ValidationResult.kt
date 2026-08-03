package com.sentinel.bridge.feature.ai.validation

/**
 * Represents the outcome of JSON validation performed by [JSONValidator].
 *
 * The validator attempts to parse the raw JSON string and, if it fails,
 * applies a repair pipeline. The result captures whether the JSON was
 * valid as-is, required repairs, or remains invalid after all repair attempts.
 */
sealed class ValidationResult {

    /**
     * The JSON string was valid on the first parse attempt with no modifications.
     *
     * @property json The original, valid JSON string.
     */
    data class Valid(val json: String) : ValidationResult()

    /**
     * The JSON string was initially invalid but was successfully repaired.
     *
     * @property json The repaired, valid JSON string.
     * @property repairs Ordered list of repair operations that were applied.
     */
    data class Repaired(val json: String, val repairs: List<String>) : ValidationResult()

    /**
     * The JSON string could not be parsed even after all repair attempts.
     *
     * @property error Description of the parse error encountered.
     */
    data class Invalid(val error: String) : ValidationResult()
}

package com.moltrax.personalnoteapp.domain.model

/**
 * An exercise's input/measurement type. Determines the data fields on the live workout screen
 * and the EXP (Progressive Overload) algorithm:
 *  - [WEIGHTLIFTING]: measured with sets / reps / weight (kg).
 *  - [BODYWEIGHT]: bodyweight move (pull up, dips, push up). Measured with reps; "Body Weight"
 *    is used instead of weight, the user may optionally enter added weight.
 *  - [DURATION]: duration-based isometric move (plank, wall sit). DURATION is entered per set instead of reps.
 *  - [CARDIO]: measured with duration (min) and steps / distance; progress is computed via pace + endurance.
 */
enum class ExerciseType(val displayName: String) {
    WEIGHTLIFTING("Ağırlık"),
    BODYWEIGHT("Vücut Ağırlığı"),
    DURATION("Süre"),
    CARDIO("Kardiyo");

    /** Whether weight-like in EXP/input terms (reps + weight)? */
    val isRepBased: Boolean get() = this == WEIGHTLIFTING || this == BODYWEIGHT

    companion object {
        fun fromName(name: String?): ExerciseType =
            entries.firstOrNull { it.name == name } ?: WEIGHTLIFTING

        /** Keywords that identify duration-based (isometric) moves by name. */
        private val DURATION_KEYWORDS = listOf(
            "plank", "hold", "wall sit", "l-sit", "l sit", "hollow", "superman",
            "dead hang", "hang", "bridge hold", "isometric", "side bridge",
        )

        /**
         * Classifies a move by bodyPart + equipment + name:
         *  - bodyPart "cardio" → [CARDIO]
         *  - name contains a duration word (plank etc.) → [DURATION]
         *  - equipment "body weight" / "assisted" → [BODYWEIGHT]
         *  - otherwise → [WEIGHTLIFTING]
         */
        fun classify(bodyPart: String?, equipment: String?, name: String?): ExerciseType {
            if (bodyPart?.contains("cardio", ignoreCase = true) == true) return CARDIO
            val lowerName = name?.lowercase().orEmpty()
            if (DURATION_KEYWORDS.any { lowerName.contains(it) }) return DURATION
            val eq = equipment?.lowercase().orEmpty()
            if (eq.contains("body weight") || eq.contains("bodyweight") || eq.contains("assisted")) {
                return BODYWEIGHT
            }
            return WEIGHTLIFTING
        }
    }
}

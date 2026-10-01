package com.moltrax.personalnoteapp.domain.model

/**
 * Bir hareketin giriş/ölçüm tipi. Canlı antrenman ekranındaki veri alanlarını ve EXP
 * (Progressive Overload) algoritmasını belirler:
 *  - [WEIGHTLIFTING]: set / tekrar / ağırlık (kg) ile ölçülür.
 *  - [BODYWEIGHT]: vücut ağırlığı hareketi (pull up, dips, push up). Tekrar ile ölçülür; ağırlık
 *    yerine "Vücut Ağırlığı" kullanılır, kullanıcı isterse ek (eklenen) ağırlık girebilir.
 *  - [DURATION]: süre bazlı izometrik hareket (plank, wall sit). Tekrar yerine set başına SÜRE girilir.
 *  - [CARDIO]: süre (dk) ve adım / mesafe ile ölçülür; gelişim tempo + dayanıklılık üzerinden hesaplanır.
 */
enum class ExerciseType(val displayName: String) {
    WEIGHTLIFTING("Ağırlık"),
    BODYWEIGHT("Vücut Ağırlığı"),
    DURATION("Süre"),
    CARDIO("Kardiyo");

    /** EXP/giriş açısından ağırlık-benzeri mi (tekrar + ağırlık)? */
    val isRepBased: Boolean get() = this == WEIGHTLIFTING || this == BODYWEIGHT

    companion object {
        fun fromName(name: String?): ExerciseType =
            entries.firstOrNull { it.name == name } ?: WEIGHTLIFTING

        /** Süre bazlı (izometrik) hareketleri ada göre tanıyan anahtar kelimeler. */
        private val DURATION_KEYWORDS = listOf(
            "plank", "hold", "wall sit", "l-sit", "l sit", "hollow", "superman",
            "dead hang", "hang", "bridge hold", "isometric", "side bridge",
        )

        /**
         * Hareketi bodyPart + equipment + ada göre sınıflandırır:
         *  - bodyPart "cardio" → [CARDIO]
         *  - ad süre kelimesi içeriyorsa (plank vb.) → [DURATION]
         *  - ekipman "body weight" / "assisted" → [BODYWEIGHT]
         *  - aksi halde → [WEIGHTLIFTING]
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

package com.moltrax.personalnoteapp.ui.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.ExerciseType

/**
 * Domain enum'ları için UI katmanı yerelleştirme yardımcıları. Enum'ların kendisi dilden bağımsız
 * kalır (saf domain); gösterilecek metin burada [stringResource] ile çözülür. Böylece dil değişince
 * etiketler de anında güncellenir.
 */

@StringRes
fun ExerciseType.labelRes(): Int = when (this) {
    ExerciseType.WEIGHTLIFTING -> R.string.exercise_type_weightlifting
    ExerciseType.BODYWEIGHT -> R.string.exercise_type_bodyweight
    ExerciseType.DURATION -> R.string.exercise_type_duration
    ExerciseType.CARDIO -> R.string.exercise_type_cardio
}

@Composable fun ExerciseType.label(): String = stringResource(labelRes())

/** Composable olmayan bağlamlar (servis/prompt) için context tabanlı etiket. */
fun ExerciseType.label(context: Context): String = context.getString(labelRes())

package com.moltrax.personalnoteapp.ui.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.ExerciseType

/**
 * UI-layer localization helpers for domain enums. The enums themselves stay language-independent
 * (pure domain); the displayed text is resolved here via [stringResource]. This way labels update
 * instantly when the language changes.
 */

@StringRes
fun ExerciseType.labelRes(): Int = when (this) {
    ExerciseType.WEIGHTLIFTING -> R.string.exercise_type_weightlifting
    ExerciseType.BODYWEIGHT -> R.string.exercise_type_bodyweight
    ExerciseType.DURATION -> R.string.exercise_type_duration
    ExerciseType.CARDIO -> R.string.exercise_type_cardio
}

@Composable fun ExerciseType.label(): String = stringResource(labelRes())

/** Context-based label for non-composable contexts (service/prompt). */
fun ExerciseType.label(context: Context): String = context.getString(labelRes())

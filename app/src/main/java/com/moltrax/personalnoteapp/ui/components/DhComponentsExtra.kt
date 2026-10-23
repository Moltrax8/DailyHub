package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moltrax.personalnoteapp.R

/** Search input with a clear action shared by local lists. */
@Composable
fun DhSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                  label: String? = null, placeholder: String? = null, onClear: (() -> Unit)? = null) {
    OutlinedTextField(value, onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), singleLine = true,
        label = label?.let { { Text(it) } }, placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty() && onClear != null) {
            { IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear)) } }
        } else null, shape = MaterialTheme.shapes.small)
}

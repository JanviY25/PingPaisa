package com.pingpaisa.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PP.TextHi, unfocusedTextColor = PP.TextHi,
    focusedBorderColor = PP.Accent, unfocusedBorderColor = PP.Divider,
    focusedLabelColor = PP.Accent, unfocusedLabelColor = PP.TextLo,
    cursorColor = PP.Accent,
    focusedContainerColor = PP.Field, unfocusedContainerColor = PP.Field,
    errorContainerColor = PP.Field, errorBorderColor = PP.Error, errorLabelColor = PP.Error,
    errorSupportingTextColor = PP.Error, errorTextColor = PP.TextHi,
    focusedSupportingTextColor = PP.TextLo, unfocusedSupportingTextColor = PP.TextLo,
    focusedPrefixColor = PP.TextLo, unfocusedPrefixColor = PP.TextLo, errorPrefixColor = PP.TextLo,
    disabledTextColor = PP.TextHi, disabledBorderColor = PP.Divider, disabledLabelColor = PP.TextLo,
    disabledContainerColor = PP.Field, disabledTrailingIconColor = PP.TextLo
)

/** Simple read-only dropdown built from stable APIs. */
@Composable
fun DropdownPicker(label: String, value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, enabled = false, singleLine = true,
            label = { Text(label) }, trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable(role = Role.DropdownList) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = PP.Surface) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o, color = PP.TextHi) }, onClick = { expanded = false; onSelect(o) })
            }
        }
    }
}

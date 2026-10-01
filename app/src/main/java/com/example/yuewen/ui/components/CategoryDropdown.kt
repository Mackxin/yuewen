package com.example.yuewen.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

@Composable
fun CategoryDropdown(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            // v2.7：去掉那圈 0.5dp 描边（用户要求「全部按钮的边框去掉」）。
            // 底色是 surface（纯白），在 background 上本来就分得清，不需要再描边。
            color = cs.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = { onSelect(cat); expanded = false },
                    leadingIcon = if (cat == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary) }
                    } else null
                )
            }
        }
    }
}

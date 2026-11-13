package io.github.aicybridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModelPicker(state: SettingsUiState, onSearch: (String) -> Unit,
                         onSelect: (String) -> Unit, onClose: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val matches = remember(state.availableModels, state.modelQuery) {
        state.availableModels.filter { it.contains(state.modelQuery.trim(), ignoreCase = true) }
    }
    OverlayDialog(show = state.modelPickerOpen, title = "选择模型", summary = "共 ${state.availableModels.size} 个 · 选择后保存渠道生效",
        onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(state.modelQuery, onValueChange = onSearch, label = "搜索模型", singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("model_search"))
            if (matches.isEmpty()) Text("没有匹配的模型，可关闭列表后手动填写。", fontSize = 13.sp)
            else LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 320.dp).testTag("model_choices"),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(matches, key = { it }) { id ->
                    val selected = state.model == id
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.tertiaryContainer else colors.surfaceContainer)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSelect(id) })
                        .padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(id, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (selected) Text("✓", fontSize = 17.sp, color = colors.primary, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            TextButton("关闭", onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("close_model_picker"))
        }
    }
}

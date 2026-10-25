package com.hiro.mistakebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiro.mistakebook.chemistry.ChemistrySymbolDatabase
import com.hiro.mistakebook.chemistry.createChemistryNormalizer
import com.hiro.mistakebook.math.MathSymbolDatabase
import com.hiro.mistakebook.math.MathSymbolEntry
import com.hiro.mistakebook.math.createNormalizer

@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val context = LocalContext.current
    val chemistryNormalizer = remember(context) {
        ChemistrySymbolDatabase.load(context).createChemistryNormalizer()
    }
    val mathNormalizer = remember(context) {
        MathSymbolDatabase.load(context).createNormalizer()
    }
    val displayText = remember(text, chemistryNormalizer, mathNormalizer) {
        mathNormalizer.normalize(chemistryNormalizer.normalize(text))
    }
    Text(
        text = displayText,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun MathSymbolPickerDialog(
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    ScienceSymbolPickerDialog(
        initialKind = ScienceSymbolKind.MATH,
        onDismiss = onDismiss,
        onInsert = onInsert
    )
}

@Composable
fun ChemistrySymbolPickerDialog(
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    ScienceSymbolPickerDialog(
        initialKind = ScienceSymbolKind.CHEMISTRY,
        onDismiss = onDismiss,
        onInsert = onInsert
    )
}

enum class ScienceSymbolKind(val label: String) {
    MATH("数学 ∑"),
    CHEMISTRY("化学 ⚗")
}

@Composable
fun ScienceSymbolPickerDialog(
    initialKind: ScienceSymbolKind,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    val context = LocalContext.current
    val mathDatabase = remember(context) { MathSymbolDatabase.load(context) }
    val chemistryDatabase = remember(context) { ChemistrySymbolDatabase.load(context) }
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    var query by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    val database = if (kind == ScienceSymbolKind.CHEMISTRY) chemistryDatabase else mathDatabase
    val visibleSymbols = remember(database, query, categoryId) {
        database.search(query, categoryId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 460.dp, max = 700.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (kind == ScienceSymbolKind.CHEMISTRY) "化学方程式符号库" else "数学符号库",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (kind == ScienceSymbolKind.CHEMISTRY) {
                                    "离线收录反应箭头、条件、离子、常见物质和方程式模板"
                                } else {
                                    "离线收录幂、根式、角、集合、向量、概率统计等高中常用符号"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, "关闭符号库")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScienceSymbolKind.entries.forEach { option ->
                            FilterChip(
                                selected = kind == option,
                                onClick = {
                                    if (kind != option) {
                                        kind = option
                                        categoryId = null
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索名称或公式") },
                        placeholder = {
                            Text(
                                if (kind == ScienceSymbolKind.CHEMISTRY) {
                                    "例如：可逆、沉淀、SO₄²⁻、离子方程式"
                                } else {
                                    "例如：平方、\\angle、根号、概率"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = categoryId == null,
                                onClick = { categoryId = null },
                                label = { Text("全部") }
                            )
                        }
                        items(database.categories.size) { index ->
                            val category = database.categories[index]
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = {
                                    categoryId = if (categoryId == category.id) null else category.id
                                },
                                label = { Text(category.name) }
                            )
                        }
                    }
                    if (visibleSymbols.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有找到相关符号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleSymbols) { entry ->
                                MathSymbolTile(entry) { onInsert(entry.insertionText) }
                            }
                        }
                    }
                    Text(
                        "点击符号会插入到当前录入框末尾；可以连续插入。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun MathSymbolTile(
    entry: MathSymbolEntry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                entry.symbol,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

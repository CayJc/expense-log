@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spacecay.expenselog.data.CatType
import com.spacecay.expenselog.data.Category
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.ColorPicker
import com.spacecay.expenselog.ui.components.IconGrid
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SettingRow
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.SolidIcon
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Palette
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Categories", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("catedit?id=0&type=$tab&parent=0") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Icon(Icons.Filled.Add, "Add")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Expense") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Income") })
            }
            val tops = bd.categories.filter { it.type == tab && it.parentId == null }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tops, key = { it.id }) { c ->
                    val i = tops.indexOf(c)
                    val subs = bd.categories.filter { it.parentId == c.id }
                    AppCard(padding = PaddingValues(4.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f)) {
                                    SettingRow(AppIcons.get(c.icon), c.name, if (subs.isEmpty()) null else "${subs.size} subcategories",
                                        iconColor = Color(c.color), onClick = { go("catedit?id=${c.id}&type=${c.type}&parent=0") })
                                }
                                IconButton(onClick = {
                                    if (i > 0) scope.launch {
                                        val mut = tops.toMutableList(); mut[i] = tops[i - 1]; mut[i - 1] = c
                                        Graph.dao.updateCategories(mut.mapIndexed { idx, x -> x.copy(sortOrder = idx) })
                                    }
                                }) { Icon(Icons.Filled.ArrowUpward, "Up") }
                                IconButton(onClick = {
                                    if (i < tops.size - 1) scope.launch {
                                        val mut = tops.toMutableList(); mut[i] = tops[i + 1]; mut[i + 1] = c
                                        Graph.dao.updateCategories(mut.mapIndexed { idx, x -> x.copy(sortOrder = idx) })
                                    }
                                }) { Icon(Icons.Filled.ArrowDownward, "Down") }
                            }
                            subs.forEach { s ->
                                Row(Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.SubdirectoryArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.weight(1f)) {
                                        SettingRow(null, s.name, onClick = { go("catedit?id=${s.id}&type=${s.type}&parent=${c.id}") })
                                    }
                                }
                            }
                            TextButton(onClick = { go("catedit?id=0&type=${c.type}&parent=${c.id}") }, modifier = Modifier.padding(start = 56.dp)) {
                                Text("+ Add subcategory")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryEditScreen(id: Long, type: Int, parentId: Long, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Category?>(null) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(Palette.at(0)) }
    var icon by remember { mutableStateOf("category") }
    var parent by remember { mutableStateOf<Long?>(if (parentId != 0L) parentId else null) }
    var catType by remember { mutableIntStateOf(type) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickParent by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }

    LaunchedEffect(id) {
        if (id != 0L) Graph.dao.getCategory(id)?.let { c ->
            orig = c; name = c.name; color = c.color; icon = c.icon; parent = c.parentId; catType = c.type
            count = Graph.dao.countCategoryTxns(id)
        } else if (parentId != 0L) Graph.dao.getCategory(parentId)?.let { p -> color = p.color; icon = p.icon }
    }
    val parentCat = parent?.let { bd.catMap[it] }
    val hasSubs = id != 0L && bd.categories.any { it.parentId == id }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(if (id == 0L) (if (parent != null) "New subcategory" else "New category") else "Edit category", back) {
                if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SolidIcon(AppIcons.get(icon), Color(color), 56.dp)
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (!hasSubs) {
                PickerField("Parent category", parentCat?.name ?: "None (top level)", parentCat?.let { AppIcons.get(it.icon) },
                    parentCat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { pickParent = true })
            }
            SectionTitle("Colour")
            ColorPicker(color) { color = it }
            SectionTitle("Icon")
            IconGrid(icon, Color(color), { icon = it }, Modifier.heightIn(max = 320.dp))
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) return@Button
                    scope.launch {
                        val o = orig
                        if (o == null) {
                            val siblings = bd.categories.count { it.type == catType && it.parentId == parent }
                            Graph.dao.insertCategory(Category(bookId = bd.bookId, name = n, type = catType, color = color, icon = icon, parentId = parent, sortOrder = siblings))
                        } else Graph.dao.updateCategory(o.copy(name = n, color = color, icon = icon, parentId = parent))
                        back()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (pickParent) {
        AlertDialog(
            onDismissRequest = { pickParent = false },
            title = { Text("Parent category") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    item { TextButton(onClick = { parent = null; pickParent = false }) { Text("None (top level)") } }
                    items(bd.categories.filter { it.type == catType && it.parentId == null && it.id != id }) { c ->
                        SettingRow(AppIcons.get(c.icon), c.name, iconColor = Color(c.color), onClick = { parent = c.id; pickParent = false })
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (confirmDelete) {
        val o = orig
        val others = bd.categories.filter { it.type == catType && it.id != id && it.parentId != id }
        var moveTo by remember { mutableStateOf(others.firstOrNull { it.name.contains("Other", true) }?.id ?: o?.parentId) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${o?.name}\"?") },
            text = {
                Column {
                    Text(if (count > 0 || hasSubs) "Its transactions will move to the category below." else "No transactions use this category.")
                    if (count > 0 || hasSubs) {
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(others) { c ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.RadioButton(moveTo == c.id, { moveTo = c.id })
                                    Text(bd.categoryLabel(c.id))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (o != null) scope.launch { Graph.repo.deleteCategory(o, moveTo); back() }
                    confirmDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

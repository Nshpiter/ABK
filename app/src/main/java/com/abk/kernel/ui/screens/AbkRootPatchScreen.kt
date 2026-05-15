@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.RootUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AbkRootPatchScreen(
    rootGranted: Boolean,
    runtimeVariant: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val bundledAssets = remember(context) { RootUtils.listBundledAbkLkmAssets(context) }
    val currentKmi = remember { RootUtils.detectCurrentKmi() }
    var selectedVariant by rememberSaveable(runtimeVariant) {
        mutableStateOf(runtimeVariant.defaultLkmVariantId())
    }
    val kmiOptions = remember(bundledAssets, selectedVariant) {
        bundledAssets
            .filter { it.variantId == selectedVariant }
            .map { it.kmi }
            .distinct()
            .sortedWith(compareBy<String> { it.substringAfter("android").substringBefore("-").toIntOrNull() ?: 0 }
                .thenBy { it.substringAfter("-") })
    }
    var selectedKmi by rememberSaveable { mutableStateOf(currentKmi.orEmpty()) }
    val selectedAsset = bundledAssets.firstOrNull {
        it.variantId == selectedVariant && it.kmi == selectedKmi
    }
    var selectedBootPath by rememberSaveable { mutableStateOf("") }
    var selectedBootName by rememberSaveable { mutableStateOf("") }
    var selectedPartition by rememberSaveable { mutableStateOf("boot") }
    var patchedImagePath by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var logLines by remember { mutableStateOf(emptyList<String>()) }
    var currentAction by remember { mutableStateOf("") }
    val userlandKsudPath = remember(context) { RootUtils.resolveUserlandKsudPath(context) }
    val canPatch = selectedBootPath.isNotBlank() && selectedAsset != null && !running &&
        (userlandKsudPath != null || rootGranted)
    val canFlash = rootGranted && patchedImagePath.isNotBlank() && !running

    LaunchedEffect(selectedVariant, kmiOptions) {
        if (selectedKmi !in kmiOptions) {
            selectedKmi = currentKmi?.takeIf { it in kmiOptions } ?: kmiOptions.firstOrNull().orEmpty()
        }
    }

    BackHandler(enabled = !running, onBack = onBack)

    fun appendLog(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            logLines = logLines + line
        }
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    val bootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val staged = withContext(Dispatchers.IO) { stageBootImageUri(context, uri) }
            selectedBootPath = staged.first.absolutePath
            selectedBootName = staged.second
            patchedImagePath = ""
            success = null
            logLines = listOf("已选择 ${staged.second}")
        }
    }

    fun startPatch() {
        val asset = selectedAsset ?: return
        running = true
        success = null
        currentAction = "修补 boot"
        patchedImagePath = ""
        logLines = listOf(
            "${'$'} ksud boot-patch --boot $selectedBootName --module ${asset.kmi}_kernelsu.ko",
            "variant: ${asset.variantLabel}",
            "kmi: ${asset.kmi}"
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.patchAbkLkmBootImage(
                    context = context,
                    bootImagePath = selectedBootPath,
                    variantId = selectedVariant,
                    kmi = selectedKmi,
                    allowRootFallback = rootGranted,
                    onOutput = ::appendLog
                )
            }
            running = false
            success = result.success
            patchedImagePath = result.patchedImagePath.orEmpty()
            if (result.output.isNotEmpty()) logLines = result.output
            if (result.success && patchedImagePath.isNotBlank()) {
                logLines = logLines + "[ABK] 输出镜像: $patchedImagePath"
            }
        }
    }

    fun startFlash() {
        if (patchedImagePath.isBlank()) return
        running = true
        success = null
        currentAction = "刷入已修补镜像"
        logLines = listOf(
            "${'$'} dd $selectedPartition <- ${File(patchedImagePath).name}",
            "file: $patchedImagePath"
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.flashImage(
                    imagePath = patchedImagePath,
                    partition = selectedPartition,
                    onOutput = ::appendLog
                )
            }
            running = false
            success = result.success
            if (result.output.isNotEmpty()) logLines = result.output
        }
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "ABK Root",
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !running) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveHeroCard(
                title = "LKM Boot 修补",
                subtitle = currentKmi?.let { "当前 KMI: $it" } ?: "当前 KMI 未自动识别",
                icon = Icons.Default.Memory,
                badge = {
                    ExpressiveStatusChip(
                        label = if (rootGranted) "可刷入" else "仅修补",
                        icon = if (rootGranted) Icons.Default.FlashOn else Icons.Default.Info,
                        color = if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    ExpressiveStatusChip(
                        label = "${bundledAssets.size} 个 LKM",
                        icon = Icons.Default.Build,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            ExpressiveSectionCard(
                title = "LKM 选择",
                subtitle = "KernelSU / SukiSU / ReSukiSU",
                icon = Icons.Default.Build
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RootUtils.ABK_LKM_VARIANTS.forEach { variant ->
                        FilterChip(
                            selected = selectedVariant == variant.id,
                            onClick = {
                                selectedVariant = variant.id
                                patchedImagePath = ""
                            },
                            label = { Text(variant.label) }
                        )
                    }
                }
                DropdownField(
                    label = "KMI",
                    value = selectedKmi.ifBlank { "无可用 LKM" },
                    options = kmiOptions.ifEmpty { listOf("无可用 LKM") },
                    onSelect = {
                        if (it in kmiOptions) {
                            selectedKmi = it
                            patchedImagePath = ""
                        }
                    }
                )
                if (selectedAsset == null) {
                    InlineWarning("当前变体和 KMI 没有内置 LKM，请等待 app 构建工作流打包最新产物。")
                }
                if (userlandKsudPath == null && !rootGranted) {
                    InlineWarning("未检测到可直接执行的 ksud；未授权 Root 时无法进行本地修补。")
                }
            }

            ExpressiveSectionCard(
                title = "Boot 镜像",
                subtitle = selectedBootName.ifBlank { "未选择" },
                icon = Icons.Default.FolderOpen
            ) {
                OutlinedButton(
                    onClick = { bootPicker.launch(arrayOf("application/octet-stream", "image/*", "*/*")) },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择 boot / init_boot 镜像")
                }
                if (selectedBootPath.isNotBlank()) {
                    Text(
                        selectedBootPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ExpressiveSectionCard(
                title = "操作",
                subtitle = if (rootGranted) "修补后可刷入指定分区" else "Root 未授权时不会刷写分区",
                icon = Icons.Default.FlashOn
            ) {
                DropdownField(
                    label = "刷入分区",
                    value = selectedPartition,
                    options = listOf("boot", "init_boot", "vendor_boot"),
                    onSelect = { selectedPartition = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = ::startPatch,
                        enabled = canPatch,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (running && currentAction == "修补 boot") {
                            CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Build, null, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("修补")
                    }
                    Button(
                        onClick = ::startFlash,
                        enabled = canFlash,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (running && currentAction == "刷入已修补镜像") {
                            CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("刷入")
                    }
                }
                if (patchedImagePath.isNotBlank()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            patchedImagePath,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { copyText("patched boot", patchedImagePath) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制路径")
                        }
                    }
                }
            }

            PatchLogCard(
                running = running,
                success = success,
                action = currentAction,
                lines = logLines,
                onReboot = {
                    if (!running) scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                }
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun InlineWarning(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PatchLogCard(
    running: Boolean,
    success: Boolean?,
    action: String,
    lines: List<String>,
    onReboot: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val icon = when {
                    running -> Icons.Default.Terminal
                    success == true -> Icons.Default.CheckCircle
                    success == false -> Icons.Default.Error
                    else -> Icons.Default.Terminal
                }
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Text(
                    action.ifBlank { "日志" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (success == true && action == "刷入已修补镜像") {
                    AssistChip(
                        onClick = onReboot,
                        label = { Text("重启") },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp, max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val displayLines = lines.ifEmpty { listOf("等待操作") }
                displayLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.firstOrNull() == '$') {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private suspend fun stageBootImageUri(context: Context, uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }
        ?.takeIf { it.isNotBlank() }
        ?: "boot.img"
    val safeName = displayName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    val dir = File(context.cacheDir, "abk-lkm-boot").apply {
        deleteRecursively()
        mkdirs()
    }
    val target = File(dir, safeName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("无法读取选择的镜像")
    target to displayName
}

private fun String.defaultLkmVariantId(): String {
    val lower = lowercase()
    return when {
        "resukisu" in lower -> "resukisu"
        "sukisu" in lower -> "sukisu"
        else -> "kernelsu"
    }
}

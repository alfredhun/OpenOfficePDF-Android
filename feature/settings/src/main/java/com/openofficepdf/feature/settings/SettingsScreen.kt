package com.openofficepdf.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统设置与合规声明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "开源许可与合规 (任务书 10.2 节)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("主项目许可证：GNU AGPL-3.0-only", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "本应用遵循完全离线、零云端依赖原则。所有源码依据 AGPL-3.0 开放，第三方开源库、字体及 AI 模型独立履行各自许可证义务。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("已锁定的合规清单文件：", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• LICENSE-AGPL-3.0.txt (主许可证全文)", style = MaterialTheme.typography.bodySmall)
                        Text("• THIRD-PARTY-NOTICES.txt (第三方版权汇总)", style = MaterialTheme.typography.bodySmall)
                        Text("• MODEL-LICENSES.md (PaddleOCR/ONNX 权重协议)", style = MaterialTheme.typography.bodySmall)
                        Text("• FONT-LICENSES.md (思源黑体 OFL-1.1 子集嵌入授权)", style = MaterialTheme.typography.bodySmall)
                        Text("• SBOM.spdx.json (CI 自动生成物料清单)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

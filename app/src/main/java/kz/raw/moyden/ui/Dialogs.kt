package kz.raw.moyden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.UpdateState
import kz.raw.moyden.data.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onPick: (Route) -> Unit) {
    val p = pal()
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.bg) {
        Column(Modifier.padding(horizontal = Pad).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Что добавить?", style = MaterialTheme.typography.headlineSmall, color = p.text)
            Spacer(Modifier.height(16.dp))
            val rows = listOf(
                listOf(Triple(kindIcon(Kind.TASK), "Дело", Route.EditEntry(null, Kind.TASK)) to p.accent,
                    Triple(kindIcon(Kind.MEDICINE), "Лекарство", Route.EditEntry(null, Kind.MEDICINE)) to p.med),
                listOf(Triple(kindIcon(Kind.EVENT), "Событие", Route.EditEntry(null, Kind.EVENT)) to p.event,
                    Triple(BirthdayIcon, "День рождения", Route.EditBirthday(null)) to p.bad),
                listOf(Triple(Icons.Outlined.ArrowUpward, "Расход", Route.EditMoney(null, false)) to p.bad,
                    Triple(Icons.Outlined.ArrowDownward, "Доход", Route.EditMoney(null, true)) to p.good),
                listOf(Triple(Icons.Outlined.Handshake, "Долг", Route.EditDebt(null)) to p.accent),
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (t, color) -> AddTile(t.first, t.second, color, Modifier.weight(1f)) { onPick(t.third) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AddTile(icon: ImageVector, title: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val p = pal()
    Row(modifier.background(p.card, MaterialTheme.shapes.large).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, 46)
        Spacer(Modifier.padding(start = 12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
    }
}

@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val st by Updater.state.collectAsStateWithLifecycle()
    val p = pal()
    when (val s = st) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Вышла новая версия ${s.release.version}") },
            text = {
                Text(if (s.release.notes.isNotBlank()) "Что нового:\n${s.release.notes}" else "Обновите приложение, чтобы получить исправления и новые возможности.")
            },
            confirmButton = { TextButton({ Updater.download(ctx, s.release) }) { Text("Обновить") } },
            dismissButton = { TextButton({ Updater.skip(ctx, s.release) }) { Text("Позже") } },
            containerColor = p.card,
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка обновления") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = p.accent)
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
            containerColor = p.card,
        )
        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Обновление скачано") },
            text = {
                Text(if (Updater.canInstall(ctx)) "Нажмите «Установить», а затем подтвердите установку."
                else "Нужно один раз разрешить установку обновлений. Включите переключатель на следующем экране, вернитесь и нажмите «Установить».")
            },
            confirmButton = {
                TextButton({
                    if (Updater.canInstall(ctx)) Updater.install(ctx, s.file) else Updater.openInstallPermission(ctx)
                }) { Text(if (Updater.canInstall(ctx)) "Установить" else "Разрешить") }
            },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text("Позже") } },
            containerColor = p.card,
        )
        else -> {}
    }
}

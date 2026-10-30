package kz.raw.moyden.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import kz.raw.moyden.data.Kind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val Pad = 20.dp

@Composable
fun pal() = LocalPalette.current

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = pal()
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onBack, Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = p.text, modifier = Modifier.size(28.dp))
                }
            } else Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = p.text,
                modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = Pad), content = {
            content()
            Spacer(Modifier.height(32.dp))
        })
        if (bottom != null) {
            Box(Modifier.fillMaxWidth().background(p.bg).navigationBarsPadding().imePadding()
                .padding(horizontal = Pad, vertical = 12.dp)) { bottom() }
        } else Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color? = null,
         padding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) {
    val p = pal()
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(color ?: p.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = pal().text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = pal().muted,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
              color: Color? = null, icon: ImageVector? = null) {
    val p = pal()
    Button(onClick, modifier.fillMaxWidth().height(60.dp), enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = color ?: p.accent, contentColor = p.onAccent)) {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
               color: Color? = null) {
    val p = pal()
    Row(modifier.clip(MaterialTheme.shapes.medium).background(p.cardAlt).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = color ?: p.text, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleSmall, color = color ?: p.text)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = pal()
    val bg by animateColorAsState(if (selected) p.accent else p.card, label = "pill")
    Box(modifier.clip(RoundedCornerShape(50)).background(bg)
        .border(BorderStroke(1.dp, if (selected) p.accent else p.muted.copy(alpha = 0.3f)), RoundedCornerShape(50))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) p.onAccent else p.text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PillGroup(options: List<Pair<T, String>>, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, t) -> Pill(t, selected(v), { onClick(v) }) }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
          number: Boolean = false, singleLine: Boolean = true, minLines: Int = 1, password: Boolean = false,
          email: Boolean = false) {
    val p = pal()
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when { number -> KeyboardType.Decimal; password -> KeyboardType.Password; email -> KeyboardType.Email; else -> KeyboardType.Text },
            capitalization = if (number || password || email) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = p.card, unfocusedContainerColor = p.card,
            focusedBorderColor = p.accent, unfocusedBorderColor = p.muted.copy(alpha = 0.3f),
            focusedLabelColor = p.accent, cursorColor = p.accent,
        ),
    )
}

@Composable
fun PickerRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit, onClear: (() -> Unit)? = null) {
    val p = pal()
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.card).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = p.muted)
            Text(value, style = MaterialTheme.typography.titleMedium, color = p.text)
        }
        if (onClear != null) TextButton(onClear) { Text("Убрать", color = p.muted) }
    }
}

@Composable
fun SettingRow(icon: ImageVector?, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null,
               trailing: @Composable (() -> Unit)? = null, tint: Color? = null) {
    val p = pal()
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(Modifier.size(44.dp).clip(CircleShape).background((tint ?: p.accent).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint ?: p.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.muted)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.muted)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit,
              icon: ImageVector? = null) {
    val p = pal()
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }, trailing = {
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = p.accent, checkedThumbColor = p.onAccent,
            uncheckedTrackColor = p.cardAlt, uncheckedBorderColor = p.muted.copy(alpha = 0.4f)))
    })
}

@Composable
fun CheckCircle(done: Boolean, color: Color, onClick: () -> Unit, size: Int = 34) {
    val p = pal()
    Box(Modifier.size((size + 14).dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(2.dp, if (done) color else p.muted.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Outlined.Check, "Готово", tint = p.onAccent, modifier = Modifier.size((size * 0.6).dp))
        }
    }
}

fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.TASK -> Icons.Outlined.TaskAlt
    Kind.MEDICINE -> Icons.Outlined.Medication
    Kind.EVENT -> Icons.Outlined.Event
}

val BirthdayIcon: ImageVector get() = Icons.Outlined.Cake

@Composable
fun kindColor(k: Kind): Color = when (k) {
    Kind.TASK -> pal().accent
    Kind.MEDICINE -> pal().med
    Kind.EVENT -> pal().event
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun EmptyNote(text: String) {
    Card(color = pal().card.copy(alpha = 0.6f)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = pal().muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
    ) { DatePicker(st, showModeToggle = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePick(initialMinute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = pal()
    val st = rememberTimePickerState(initialMinute / 60, initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPick(st.hour * 60 + st.minute); onDismiss() }) { Text("Готово") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        title = { Text("Время") },
        text = {
            TimePicker(st, colors = TimePickerDefaults.colors(
                selectorColor = p.accent, timeSelectorSelectedContainerColor = p.accent.copy(alpha = 0.25f),
                clockDialColor = p.cardAlt))
        },
        containerColor = p.card,
    )
}

@Composable
fun Confirm(title: String, text: String, ok: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onOk(); onDismiss() }) { Text(ok, color = pal().bad) } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        title = { Text(title) }, text = { Text(text) }, containerColor = pal().card,
    )
}

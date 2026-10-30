package kz.raw.moyden.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.data.Cloud
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Updater
import kz.raw.moyden.data.Weather
import kz.raw.moyden.notify.Notifs

sealed interface Route {
    data object Home : Route
    data class EditEntry(val id: String?, val kind: Kind = Kind.TASK, val date: Long? = null) : Route
    data class EditBirthday(val id: String?) : Route
    data class EditMoney(val id: String?, val income: Boolean = false) : Route
    data class EditDebt(val id: String?) : Route
    data class Entries(val kind: Kind?) : Route
    data object Birthdays : Route
    data object Account : Route
    data object Appearance : Route
    data object Notifications : Route
    data object Health : Route
    data object City : Route
    data object Updates : Route
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val top get() = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MoyDenTheme { Root(this) } }
    }
}

@Composable
private fun Root(activity: ComponentActivity) {
    val p = pal()
    LaunchedEffect(p) {
        val style = if (p.dark) SystemBarStyle.dark(p.bg.toArgb()) else SystemBarStyle.light(p.bg.toArgb(), p.bg.toArgb())
        activity.enableEdgeToEdge(style, style)
    }
    val ctx = LocalContext.current
    val nav = remember { Nav() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifs.allowed(ctx)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        Weather.refresh()
        Cloud.syncNow()
        if (Settings.p.autoUpdate) Updater.check(ctx, auto = true)
    }

    BackHandler(enabled = nav.stack.size > 1 || tab != 0) {
        if (nav.stack.size > 1) nav.back() else tab = 0
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        AnimatedContent(nav.top, label = "nav", transitionSpec = {
            if (targetState is Route.Home) (fadeIn() togetherWith fadeOut())
            else (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
        }) { r ->
            when (r) {
                Route.Home -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> TodayScreen(nav)
                            1 -> CalendarScreen(nav)
                            2 -> MoneyScreen(nav)
                            else -> MoreScreen(nav)
                        }
                    }
                    BottomBar(tab, { tab = it }, { addOpen = true })
                }
                is Route.EditEntry -> EntryEditor(nav, r)
                is Route.EditBirthday -> BirthdayEditor(nav, r.id)
                is Route.EditMoney -> MoneyEditor(nav, r.id, r.income)
                is Route.EditDebt -> DebtEditor(nav, r.id)
                is Route.Entries -> EntriesScreen(nav, r.kind)
                Route.Birthdays -> BirthdaysScreen(nav)
                Route.Account -> AccountScreen(nav)
                Route.Appearance -> AppearanceScreen(nav)
                Route.Notifications -> NotificationsScreen(nav)
                Route.Health -> HealthScreen(nav)
                Route.City -> CityScreen(nav)
                Route.Updates -> UpdatesScreen(nav)
            }
        }
        if (addOpen) AddSheet(onDismiss = { addOpen = false }) { r -> addOpen = false; nav.go(r) }
        UpdateDialog()
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val p = pal()
    Box(Modifier.fillMaxWidth().background(p.card).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
            TabItem(Icons.Outlined.WbSunny, "Сегодня", tab == 0, Modifier.weight(1f)) { onTab(0) }
            TabItem(Icons.Outlined.CalendarMonth, "Календарь", tab == 1, Modifier.weight(1f)) { onTab(1) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(y = (-14).dp).size(64.dp).shadow(8.dp, CircleShape).clip(CircleShape)
                    .background(p.accent).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, "Добавить", tint = p.onAccent, modifier = Modifier.size(34.dp))
                }
            }
            TabItem(Icons.Outlined.AccountBalanceWallet, "Деньги", tab == 2, Modifier.weight(1f)) { onTab(2) }
            TabItem(Icons.Outlined.Menu, "Ещё", tab == 3, Modifier.weight(1f)) { onTab(3) }
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = pal()
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.clip(CircleShape).background(if (selected) p.accent.copy(alpha = 0.16f) else p.card)
            .padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(icon, label, tint = if (selected) p.accent else p.muted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) p.text else p.muted, maxLines = 1)
    }
}

package kz.raw.moyden

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kz.raw.moyden.data.Birthday
import kz.raw.moyden.data.Debt
import kz.raw.moyden.data.Entry
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.Money
import kz.raw.moyden.data.Repeat
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.Weather
import kz.raw.moyden.data.ep
import kz.raw.moyden.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class UiFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val out = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shot(name: String) {
        rule.waitForIdle()
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun seed(ctx: Context) {
        val t = LocalDate.now()
        val days = JSONArray((0 until 7).map { t.plusDays(it.toLong()).toString() })
        val cache = JSONObject()
            .put("current", JSONObject().put("temperature_2m", 7.4).put("apparent_temperature", 4.1)
                .put("weather_code", 2).put("wind_speed_10m", 11.0).put("relative_humidity_2m", 60).put("is_day", 1))
            .put("daily", JSONObject().put("time", days)
                .put("weather_code", JSONArray(listOf(2, 3, 61, 0, 71, 1, 2)))
                .put("temperature_2m_max", JSONArray(listOf(9, 8, 6, 11, 2, 5, 7)))
                .put("temperature_2m_min", JSONArray(listOf(1, 0, 2, 3, -3, -1, 0)))
                .put("precipitation_probability_max", JSONArray(listOf(10, 20, 80, 0, 60, 10, 5))))
            .put("_city", "Алматы").put("_at", System.currentTimeMillis())
        ctx.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().putString("cache", cache.toString()).commit()
        Weather.reloadCache()
        Settings.update { it.copy(userName = "мама", autoUpdate = false) }

        Store.save(Entry(kind = Kind.MEDICINE, title = "Конкор", note = "1 таблетка после завтрака", date = t.minusDays(5).ep(),
            times = listOf(8 * 60, 20 * 60), repeat = Repeat.DAILY, loud = true,
            done = setOf("${t.ep()}:480")))
        Store.save(Entry(kind = Kind.TASK, title = "Позвонить Максиму", date = t.ep(), times = listOf(19 * 60)))
        Store.save(Entry(kind = Kind.EVENT, title = "Запись к врачу", note = "Поликлиника, кабинет 12", date = t.ep(),
            times = listOf(15 * 60 + 30), remindBefore = 60))
        Store.save(Entry(kind = Kind.TASK, title = "Оплатить коммуналку", date = t.ep()))
        Store.save(Birthday(name = "Сестра Ира", day = t.plusDays(3).dayOfMonth, month = t.plusDays(3).monthValue, year = 1968))
        Store.save(Money(amount = 12500.0, category = "Продукты", date = t.ep(), note = "Магнум"))
        Store.save(Money(amount = 4300.0, category = "Аптека", date = t.ep()))
        Store.save(Money(amount = 98000.0, income = true, category = "Пенсия", date = t.withDayOfMonth(1).ep()))
        Store.save(Debt(person = "Соседка Галя", amount = 20000.0, theyOweMe = true, date = t.minusDays(10).ep(), due = t.plusDays(2).ep()))
    }

    @Test fun mainFlow() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        seed(ctx)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            scenario = sc
            rule.onNodeWithText("Дела на сегодня").assertExists()
            shot("01_today")
            rule.onNodeWithText("Запись к врачу").performScrollTo()
            shot("02_today_scrolled")

            rule.onNodeWithContentDescription("Добавить").performClick()
            shot("03_add_sheet")
            rule.onAllNodesWithText("Лекарство")[0].performClick()
            rule.onNode(hasSetTextAction() and hasText("Название лекарства")).performTextInput("Витамин D")
            shot("04_medicine_editor")
            rule.onNodeWithText("Сохранить").performClick()
            rule.waitForIdle()
            check(Store.data.value.liveEntries.any { it.title == "Витамин D" && it.times.isNotEmpty() && it.loud })

            rule.onNodeWithText("Календарь").performClick()
            shot("05_calendar")
            rule.onNodeWithText("Деньги").performClick()
            shot("06_money")
            rule.onNodeWithText("Долги").performClick()
            shot("07_debts")
            rule.onNodeWithText("Ещё").performClick()
            shot("08_more")
            rule.onNodeWithText("Оформление").performScrollTo().performClick()
            shot("09_appearance")
            rule.onNodeWithText("Графит").performClick()
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Уведомления и звук").performScrollTo().performClick()
            shot("10_notifications")
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Проверка напоминаний").performScrollTo().performClick()
            shot("11_health")
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Войти в аккаунт").performScrollTo().performClick()
            shot("12_account")
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Обновления").performScrollTo().performClick()
            shot("13_updates")
            rule.onNodeWithContentDescription("Назад").performClick()
            rule.onNodeWithText("Сегодня").performClick()
            shot("14_today_dark")
            rule.onNodeWithContentDescription("Добавить").performClick()
            rule.onNodeWithText("Долг").performClick()
            shot("15_debt_editor")
        }
    }
}

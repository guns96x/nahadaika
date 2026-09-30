package ua.nahadaika

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ua.nahadaika.data.Repeat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class VoiceParserTest {
    private lateinit var original: TimeZone

    // Середа, 30 вересня 2026, 10:00
    private val now = LocalDateTime.of(2026, 9, 30, 10, 0)

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(original)

    private fun check(phrase: String, text: String, at: LocalDateTime?, repeat: Repeat = Repeat.NONE) {
        val cmd = VoiceParser.parse(phrase, now)
        val parsedAt = cmd.at?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        assertEquals("час для «$phrase»", at, parsedAt)
        assertEquals("текст для «$phrase»", text, cmd.text)
        assertEquals("повтор для «$phrase»", repeat, cmd.repeat)
    }

    private fun d(month: Int, day: Int, h: Int, m: Int = 0, year: Int = 2026) = LocalDateTime.of(year, month, day, h, m)

    @Test fun tomorrowAtHour() = check("Нагадай завтра о 9 купити хліб", "Купити хліб", d(10, 1, 9))
    @Test fun inHours() = check("через 2 години подзвонити мамі", "Подзвонити мамі", d(9, 30, 12))
    @Test fun inOneHour() = check("через годину зняти пиріг", "Зняти пиріг", d(9, 30, 11))
    @Test fun inHalfHour() = check("через пів години вимкнути духовку", "Вимкнути духовку", d(9, 30, 10, 30))
    @Test fun inHalfHourOneWord() = check("через півгодини вимкнути духовку", "Вимкнути духовку", d(9, 30, 10, 30))
    @Test fun inMinutesNoText() = check("через 15 хвилин", "", d(9, 30, 10, 15))
    @Test fun inHoursAndMinutes() = check("через 1 годину 30 хвилин забрати білизну", "Забрати білизну", d(9, 30, 11, 30))

    @Test fun commandWithClockTime() =
        check("постав мені нагадування на 18:30 забрати дитину", "Забрати дитину", d(9, 30, 18, 30))

    @Test fun pastTimeMovesToTomorrow() = check("нагадай о 8 ранку випити таблетку", "Випити таблетку", d(10, 1, 8))
    @Test fun evening() = check("о 7 вечора зустріч з Андрієм", "Зустріч з Андрієм", d(9, 30, 19))
    @Test fun eveningWithoutHour() = check("нагадай завтра ввечері винести сміття", "Винести сміття", d(10, 1, 20))
    @Test fun halfPast() = check("о пів на восьму вечора кіно", "Кіно", d(9, 30, 19, 30))
    @Test fun ordinalHour() = check("о дев'ятій тридцять нарада", "Нарада", d(10, 1, 9, 30))
    @Test fun ordinalHourEvening() = check("сьогодні о шостій вечора тренування", "Тренування", d(9, 30, 18))
    @Test fun night() = check("об 11 ночі поставити телефон на зарядку", "Поставити телефон на зарядку", d(9, 30, 23))

    @Test fun weekday() = check("в п'ятницю о 15:00 перевзутися", "Перевзутися", d(10, 2, 15))
    @Test fun sameWeekdayLaterToday() = check("у середу о 20:00 футбол", "Футбол", d(9, 30, 20))
    @Test fun sameWeekdayAlreadyPassed() = check("у середу о 9 футбол", "Футбол", d(10, 7, 9))
    @Test fun dayAfterTomorrow() = check("нагадай що післязавтра в 11:15 лікар", "Лікар", d(10, 2, 11, 15))
    @Test fun inDaysWithTime() = check("через 3 дні о 10 оплатити інтернет", "Оплатити інтернет", d(10, 3, 10))
    @Test fun inWeekKeepsTime() = check("через тиждень поміняти фільтр", "Поміняти фільтр", d(10, 7, 10))

    @Test fun monthNameDate() = check("25 жовтня день народження мами", "День народження мами", d(10, 25, 9))
    @Test fun numericDate() = check("5.10 о 12 забрати посилку", "Забрати посилку", d(10, 5, 12))
    @Test fun pastDateNextYear() = check("1 вересня о 8 лінійка", "Лінійка", d(9, 1, 8, year = 2027))

    @Test fun daily() = check("щодня о 8 вітаміни", "Вітаміни", d(10, 1, 8), Repeat.DAILY)
    @Test fun everyMonday() = check("кожного понеділка о 10 планірка", "Планірка", d(10, 5, 10), Repeat.WEEKLY)
    @Test fun everyFridayShort() = check("щоп'ятниці о 18 прибирання", "Прибирання", d(10, 2, 18), Repeat.WEEKLY)
    @Test fun yearly() = check("щороку 8 березня привітати маму", "Привітати маму", d(3, 8, 9, year = 2027), Repeat.YEARLY)
    @Test fun monthly() = check("щомісяця 5 числа оплатити квартиру о 10", "Оплатити квартиру", d(10, 5, 10), Repeat.MONTHLY)

    @Test fun noTimeKeepsText() {
        val cmd = VoiceParser.parse("купити молоко", now)
        assertNull(cmd.at)
        assertEquals("Купити молоко", cmd.text)
    }

    @Test fun numbersInTextAreNotTimes() = check("завтра купити 2 кг картоплі", "Купити 2 кг картоплі", d(10, 1, 9))

    // Так пише офлайн-розпізнавач: числа словами, без розділових знаків.
    @Test fun spokenOrdinalHour() = check("нагадай завтра о дев'ятій купити хліб", "Купити хліб", d(10, 1, 9))
    @Test fun spokenEvening() = check("завтра о сьомій вечора забрати посилку", "Забрати посилку", d(10, 1, 19))
    @Test fun spokenHourMinutes() = check("сьогодні о вісімнадцять тридцять тренування", "Тренування", d(9, 30, 18, 30))
    @Test fun spokenOrdinalHourMinutes() = check("о вісімнадцятій п'ятнадцять нарада", "Нарада", d(9, 30, 18, 15))
    @Test fun spokenInMinutes() = check("нагадаю через двадцять хвилин вимкнути духовку", "Вимкнути духовку", d(9, 30, 10, 20))
    @Test fun spokenInHours() = check("через дві години подзвонити мамі", "Подзвонити мамі", d(9, 30, 12))
    @Test fun spokenDate() = check(
        "нагадай двадцять п'ятого жовтня о дванадцять й день народження", "День народження", d(10, 25, 12),
    )
    @Test fun spokenCardinalHour() = check("в п'ятницю о дев'ять сорок п'ять здати звіт", "Здати звіт", d(10, 2, 9, 45))
    @Test fun hourWithoutPreposition() = check("завтра вісім ранку пробіжка", "Пробіжка", d(10, 1, 8))

    // Російською та суржиком.
    @Test fun russianTomorrow() = check("напомни завтра в девять утра позвонить врачу", "Позвонить врачу", d(10, 1, 9))
    @Test fun surzhykTomorrow() = check("напомню завтра в дев'ять утра подзвонить врачу", "Подзвонить врачу", d(10, 1, 9))
    @Test fun russianInHour() = check("через час забрать детей", "Забрать детей", d(9, 30, 11))
    @Test fun russianInMinutes() = check("через пятнадцать минут выключить плиту", "Выключить плиту", d(9, 30, 10, 15))
    @Test fun russianWeekday() = check("в пятницу в семь вечера кино", "Кино", d(10, 2, 19))
    @Test fun russianDate() = check("двадцать пятого октября в двенадцать день рождения", "День рождения", d(10, 25, 12))
    @Test fun russianDaily() = check("каждый день в восемь витамины", "Витамины", d(10, 1, 8), Repeat.DAILY)
    @Test fun russianDayAfterTomorrow() = check("послезавтра в 11 стоматолог", "Стоматолог", d(10, 2, 11))
    @Test fun wordNumbersInTextStay() = check("завтра купити три хліби", "Купити 3 хліби", d(10, 1, 9))

    @Test fun customDefaultTime() {
        val cmd = VoiceParser.parse("завтра купити хліб", now, defaultTime = java.time.LocalTime.of(8, 0))
        assertEquals(d(10, 1, 8), LocalDateTime.ofInstant(Instant.ofEpochMilli(cmd.at!!), ZoneId.systemDefault()))
    }

    private fun dt(h: Int, m: Int, sec: Int) = LocalDateTime.of(2026, 9, 30, h, m, sec)

    // Час перед командою, секунди, таймер і будильник.
    @Test fun timeBeforeCommand() = check("через пів години нагадай вимкнути плиту", "Вимкнути плиту", d(9, 30, 10, 30))
    @Test fun halfHourThenCommandOnly() = check("через півгодини нагадай", "", d(9, 30, 10, 30))
    @Test fun inSeconds() = check("через 10 секунд", "", dt(10, 0, 10))
    @Test fun inSecondsWords() = check("нагадай через десять секунд зняти чайник", "Зняти чайник", dt(10, 0, 10))
    @Test fun halfMinute() = check("через півхвилини", "", dt(10, 0, 30))
    @Test fun minuteAndSeconds() = check("через хвилину і 20 секунд", "", dt(10, 1, 20))
    @Test fun timerMinutes() = check("постав таймер на 10 хвилин", "Таймер", d(9, 30, 10, 10))
    @Test fun timerSeconds() = check("таймер на 30 секунд", "Таймер", dt(10, 0, 30))
    @Test fun timerWithText() = check("постав таймер на 15 хвилин макарони", "Макарони", d(9, 30, 10, 15))
    @Test fun timerStartKeyword() = check("засічи 5 хвилин", "Таймер", d(9, 30, 10, 5))
    @Test fun alarmMorning() = check("постав будильник на 7 ранку", "Будильник", d(10, 1, 7))
    @Test fun alarmClock() = check("будильник на 6:30", "Будильник", d(10, 1, 6, 30))
    @Test fun wakeMeUp() = check("розбуди мене завтра о пів на восьму", "Будильник", d(10, 1, 7, 30))
    @Test fun oClockLocative() = check("о 9 годині нарада", "Нарада", d(10, 1, 9))
    @Test fun durationIsNotClockTime() = check("завтра зустріч на 2 години", "Зустріч на 2 години", d(10, 1, 9))

    // Розмовні варіації.
    @Test fun inAMinuteColloquial() = check("через хвилинку", "", d(9, 30, 10, 1))
    @Test fun inAnHourColloquial() = check("через годинку забрати пральку", "Забрати пральку", d(9, 30, 11))
    @Test fun invertedMinutes() = check("хвилин через 10 перевірити пиріг", "Перевірити пиріг", d(9, 30, 10, 10))
    @Test fun invertedWords() = check("хвилин за двадцять вийти", "Вийти", d(9, 30, 10, 20))
    @Test fun zaMinutes() = check("нагадай за 5 хвилин", "", d(9, 30, 10, 5))
    @Test fun zaBeforeIsNotRelative() = check("за 5 хвилин до зустрічі", "За 5 хвилин до зустрічі", null)
    @Test fun coupleOfHours() = check("через пару годин подзвонити в сервіс", "Подзвонити в сервіс", d(9, 30, 12))
    @Test fun fewMinutes() = check("через кілька хвилин", "", d(9, 30, 10, 5))
    @Test fun quarterHour() = check("через чверть години", "", d(9, 30, 10, 15))
    @Test fun hourAndQuarter() = check("через годину з чвертю", "", d(9, 30, 11, 15))
    @Test fun hourAndHalf() = check("через півтори години", "", d(9, 30, 11, 30))
    @Test fun quarterPast() = check("о чверть на восьму вечора кіно", "Кіно", d(9, 30, 19, 15))
    @Test fun quarterTo() = check("без чверті вісім ранку зарядка", "Зарядка", d(10, 1, 7, 45))
    @Test fun tenTo() = check("завтра без десяти дев'ять", "", d(10, 1, 8, 50))
    @Test fun lunch() = check("в обід подзвонити бухгалтеру", "Подзвонити бухгалтеру", d(9, 30, 13))
    @Test fun afterWork() = check("після роботи купити хліб", "Купити хліб", d(9, 30, 18, 30))
    @Test fun beforeSleep() = check("перед сном випити таблетку", "Випити таблетку", d(9, 30, 22))
    @Test fun everyMorning() = check("щоранку зарядка", "Зарядка", d(10, 1, 9), Repeat.DAILY)
    @Test fun everyEvening() = check("щовечора полити квіти", "Полити квіти", d(9, 30, 20), Repeat.DAILY)
    @Test fun weekend() = check("на вихідних прибрати балкон", "Прибрати балкон", d(10, 3, 9))
    @Test fun nextWeek() = check("наступного тижня записатися до лікаря", "Записатися до лікаря", d(10, 5, 9))
    @Test fun endOfWeek() = check("в кінці тижня здати звіт", "Здати звіт", d(10, 2, 9))
    @Test fun inAYear() = check("через рік продовжити страховку", "Продовжити страховку", d(9, 30, 10, year = 2027))
    @Test fun russianColloquial() = check("через пару минут выключить", "Выключить", d(9, 30, 10, 2))
    @Test fun russianLunch() = check("завтра в обед встреча", "Встреча", d(10, 1, 13))
    @Test fun russianToPreposition() = check("к 9 утра позвонить в банк", "Позвонить в банк", d(10, 1, 9))
    @Test fun alarmHalfPast() = check("постав будильник на пів на сьому", "Будильник", d(10, 1, 6, 30))
    @Test fun timerHourAndHalf() = check("таймер на півтори години", "Таймер", d(9, 30, 11, 30))
}

package ua.nahadaika

import ua.nahadaika.data.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Результат розбору голосової команди. [at] = null, якщо час не вдалося зрозуміти; [text] може бути порожнім. */
data class VoiceCommand(val text: String, val at: Long?, val repeat: Repeat)

/**
 * Розбирає фрази на кшталт «нагадай завтра о 9 купити хліб», «через 2 години подзвонити мамі»,
 * «щопонеділка о 10 планірка», «25 жовтня о 12 день народження».
 * Все, що не стосується часу, стає текстом нагадування.
 */
object VoiceParser {
    // Межі слова з урахуванням кирилиці та апострофа (\b у Java працює лише з латиницею).
    private const val B = "(?<![\\p{L}\\d'])"
    private const val E = "(?![\\p{L}\\d'])"

    private val numberWords = mapOf(
        "один" to 1.0, "одна" to 1.0, "одну" to 1.0, "два" to 2.0, "дві" to 2.0, "три" to 3.0,
        "чотири" to 4.0, "п'ять" to 5.0, "шість" to 6.0, "сім" to 7.0, "вісім" to 8.0,
        "дев'ять" to 9.0, "десять" to 10.0, "одинадцять" to 11.0, "дванадцять" to 12.0,
        "п'ятнадцять" to 15.0, "двадцять" to 20.0, "тридцять" to 30.0, "сорок" to 40.0,
        "п'ятдесят" to 50.0, "півтори" to 1.5, "пів" to 0.5,
    )
    private val NUM = "(\\d+(?:[.,]\\d+)?|" + numberWords.keys.sortedByDescending { it.length }.joinToString("|") + ")"

    // «пів на восьму»
    private val ordinalAccusative = listOf(
        "першу", "другу", "третю", "четверту", "п'яту", "шосту", "сьому", "восьму",
        "дев'яту", "десяту", "одинадцяту", "дванадцяту",
    )

    private val months = listOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня",
    ).withIndex().associate { it.value to it.index + 1 } + listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    ).withIndex().associate { it.value to it.index + 1 }
    private val MONTH = "(" + months.keys.joinToString("|") + ")"

    // ---- Числа словами → цифри: розпізнавач часто пише «вісімнадцять тридцять», «двадцять п'ятого» ----
    private val units = mapOf(
        "нуль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "одне" to 1, "два" to 2, "дві" to 2, "две" to 2,
        "три" to 3, "чотири" to 4, "четыре" to 4, "п'ять" to 5, "пять" to 5, "шість" to 6, "шесть" to 6,
        "сім" to 7, "семь" to 7, "вісім" to 8, "восемь" to 8, "дев'ять" to 9, "девять" to 9,
    )
    private val teens = mapOf(
        "десять" to 10, "одинадцять" to 11, "одиннадцать" to 11, "дванадцять" to 12, "двенадцать" to 12,
        "тринадцять" to 13, "тринадцать" to 13, "чотирнадцять" to 14, "четырнадцать" to 14,
        "п'ятнадцять" to 15, "пятнадцать" to 15, "шістнадцять" to 16, "шестнадцать" to 16,
        "сімнадцять" to 17, "семнадцать" to 17, "вісімнадцять" to 18, "восемнадцать" to 18,
        "дев'ятнадцять" to 19, "девятнадцать" to 19,
    )
    private val tens = mapOf(
        "двадцять" to 20, "двадцать" to 20, "тридцять" to 30, "тридцать" to 30,
        "сорок" to 40, "п'ятдесят" to 50, "пятьдесят" to 50,
    )
    private val ordinalStems = listOf(
        "перш" to 1, "перв" to 1, "друг" to 2, "втор" to 2, "трет" to 3, "четверт" to 4, "п'ят" to 5, "пят" to 5,
        "шост" to 6, "шест" to 6, "сьом" to 7, "седьм" to 7, "восьм" to 8, "дев'ят" to 9, "девят" to 9,
        "десят" to 10, "одинадцят" to 11, "одиннадцат" to 11, "дванадцят" to 12, "двенадцат" to 12,
        "тринадцят" to 13, "тринадцат" to 13, "чотирнадцят" to 14, "четырнадцат" to 14,
        "п'ятнадцят" to 15, "пятнадцат" to 15, "шістнадцят" to 16, "шестнадцат" to 16,
        "сімнадцят" to 17, "семнадцат" to 17, "вісімнадцят" to 18, "восемнадцат" to 18,
        "дев'ятнадцят" to 19, "девятнадцат" to 19, "двадцят" to 20, "двадцат" to 20, "тридцят" to 30, "тридцат" to 30,
    ).sortedByDescending { it.first.length }

    private fun alt(words: Collection<String>) = words.sortedByDescending { it.length }.joinToString("|")
    private val TENS = "(${alt(tens.keys)})"
    private val ORD = "(${alt(ordinalStems.map { it.first })})"
    private val ordinalHour = Regex("$B(о|об|в|у)\\s+(?:$TENS\\s+)?${ORD}ій$E", RegexOption.IGNORE_CASE)
    private val ordinalDay = Regex("$B(?:$TENS\\s+)?$ORD(?:ого|ього|его|ьего|е)(?=\\s+(?:$MONTH|числа)$E)", RegexOption.IGNORE_CASE)
    private val compound = Regex("$B$TENS(?:\\s+(${alt(units.keys - "нуль")}))?$E", RegexOption.IGNORE_CASE)
    private val simple = Regex("$B(${alt(units.keys + teens.keys)})$E", RegexOption.IGNORE_CASE)

    private fun ordinal(tensWord: String?, stem: String): Int =
        (tensWord?.let { tens[it.lowercase()] } ?: 0) + ordinalStems.first { stem.lowercase() == it.first }.second

    private fun normalizeNumbers(input: String): String = input
        .replace(ordinalHour) { m ->
            val h = ordinal(m.groups[2]?.value, m.groupValues[3])
            if (h in 1..24) "${m.groupValues[1]} ${h % 24}" else m.value
        }
        .replace(ordinalDay) { m -> ordinal(m.groups[1]?.value, m.groupValues[2]).toString() }
        .replace(compound) { m ->
            ((tens[m.groupValues[1].lowercase()] ?: 0) + (m.groups[2]?.value?.let { units[it.lowercase()] } ?: 0)).toString()
        }
        .replace(simple) { m -> (units[m.value.lowercase()] ?: teens.getValue(m.value.lowercase())).toString() }

    private val weekdayStems = listOf(
        "понеділ" to DayOfWeek.MONDAY, "вівтор" to DayOfWeek.TUESDAY, "серед" to DayOfWeek.WEDNESDAY,
        "четвер" to DayOfWeek.THURSDAY, "п'ятниц" to DayOfWeek.FRIDAY, "субот" to DayOfWeek.SATURDAY,
        "неділ" to DayOfWeek.SUNDAY,
        "понедельн" to DayOfWeek.MONDAY, "вторн" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY,
        "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY, "воскресен" to DayOfWeek.SUNDAY,
    )
    private val WEEKDAY = "(" + weekdayStems.joinToString("|") { it.first } + ")\\p{L}*"

    private const val EVERY = "(?:кожн|кажд)\\p{L}*\\s+"
    private const val HOURS = "(?:годин\\p{L}*|год|час(?:а|ов)?)"
    private const val MINUTES = "(?:хвилин\\p{L}*|хв|минут\\p{L}*|мин)"
    private const val PERIOD = "(?:ранку|вранці|зранку|утра|вечора|ввечері|вечера|дня|ночі|ночи)"
    // Після числа — одиниця виміру, тож це не година («на 5 днів», «2 кг»).
    private const val UNIT = "(?:дн|тиж|міс|хв|мин|раз|рок|кг|шт|грн|%|[.,/]\\d)"

    private fun num(token: String?): Double? {
        if (token == null) return null
        return token.replace(',', '.').toDoubleOrNull() ?: numberWords[token]
    }

    private fun weekday(stem: String) = weekdayStems.first { stem.startsWith(it.first) }.second

    /**
     * Рядок, з якого по черзі «вирізаються» розпізнані шматки. Пошук іде по малих літерах,
     * а вирізається паралельно й з оригіналу, щоб у тексті лишились великі літери («Андрію»).
     */
    private class Cursor(original: String) {
        var text = original
        var s = original.lowercase()

        init {
            if (s.length != text.length) text = s
        }

        fun take(pattern: String): MatchResult? {
            val m = Regex(pattern).find(s) ?: return null
            s = s.replaceRange(m.range, " ")
            text = text.replaceRange(m.range, " ")
            return m
        }
    }

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): VoiceCommand {
        val base = now.truncatedTo(ChronoUnit.MINUTES)
        val c = Cursor(
            " " + normalizeNumbers(
                input
                    .replace(Regex("[’ʼ`‘]"), "'")
                    .replace(Regex("\\s+"), " ")
                    .trim(),
            ) + " ",
        )

        // ---- Повтор ----
        var repeat = Repeat.NONE
        var repeatWeekday: DayOfWeek? = null
        c.take("$B(?:що|(?:кожн|кажд)\\p{L}*\\s+|по\\s+)$WEEKDAY$E")?.let {
            repeat = Repeat.WEEKLY
            repeatWeekday = weekday(it.groupValues[1])
        }
        if (repeat == Repeat.NONE) {
            val repeats = listOf(
                "(?:щодня|щоденно|ежедневно|$EVERY(?:дня|день))" to Repeat.DAILY,
                "(?:щотижня|щотижнево|еженедельно|$EVERY(?:тижня|тиждень|неделю))" to Repeat.WEEKLY,
                "(?:щомісяця|щомісячно|ежемесячно|$EVERY(?:місяця|місяць|месяц))" to Repeat.MONTHLY,
                "(?:щороку|щорічно|ежегодно|$EVERY(?:року|рік|год))" to Repeat.YEARLY,
            )
            for ((p, r) in repeats) {
                if (c.take("$B$p$E") != null) {
                    repeat = r
                    break
                }
            }
        }

        // ---- Відносний час: «через …» ----
        var absolute: LocalDateTime? = null
        var dayOffset: Long? = null
        var monthOffset: Long? = null
        c.take("${B}через\\s+(?:півгодини|полчаса)$E")?.let { absolute = base.plusMinutes(30) }
        if (absolute == null) {
            c.take("${B}через\\s+(?:$NUM\\s*)?$HOURS(?:\\s+(?:і\\s+|та\\s+|и\\s+)?$NUM\\s*$MINUTES)?$E")?.let {
                val hours = num(it.groups[1]?.value) ?: 1.0
                val minutes = num(it.groups[2]?.value) ?: 0.0
                absolute = base.plusMinutes((hours * 60 + minutes).toLong())
            }
        }
        if (absolute == null) {
            c.take("${B}через\\s+(?:$NUM\\s*)?$MINUTES$E")?.let {
                absolute = base.plusMinutes((num(it.groups[1]?.value) ?: 1.0).toLong())
            }
        }
        c.take("${B}через\\s+(?:$NUM\\s*)?(?:дн\\p{L}*|день|добу|сутки|суток)$E")?.let {
            dayOffset = (num(it.groups[1]?.value) ?: 1.0).toLong()
        }
        c.take("${B}через\\s+(?:$NUM\\s*)?(?:тиждень|тижні|тижнів|неделю|недели|недель)$E")?.let {
            dayOffset = 7 * (num(it.groups[1]?.value) ?: 1.0).toLong()
        }
        c.take("${B}через\\s+(?:$NUM\\s*)?(?:місяць|місяці|місяців|месяц|месяца|месяцев)$E")?.let {
            monthOffset = (num(it.groups[1]?.value) ?: 1.0).toLong()
        }

        // ---- Час доби ----
        var time: LocalTime? = null
        var hourIsExplicit12h = false // «о 7» без хвилин — може бути і ранок, і вечір
        c.take("$B(?:опівдні|(?:в\\s+)?полдень)$E")?.let { time = LocalTime.NOON }
        if (time == null) c.take("$B(?:опівночі|(?:в\\s+)?полночь)$E")?.let { time = LocalTime.MIDNIGHT }
        if (time == null) {
            c.take("$B(?:о|об|в|у|на)?\\s*пів\\s*на\\s+(\\d{1,2}|${ordinalAccusative.joinToString("|")})$E")?.let {
                val h = it.groupValues[1].toIntOrNull() ?: (ordinalAccusative.indexOf(it.groupValues[1]) + 1)
                time = LocalTime.of((h - 1 + 24) % 24, 30)
                hourIsExplicit12h = true
            }
        }
        if (time == null) {
            // «о 9», «в 18:30», «о 18 30», «о 9 годині 15 хвилин»; або без прийменника — «9 ранку».
            val clock = "(\\d{1,2})(?:[:.](\\d{2})|\\s+([0-5]\\d)(?!\\s*(?:$UNIT|$MONTH|числа)))?(?!\\s*$UNIT)" +
                "(?:\\s+(?:годин\\p{L}*|час\\p{L}*))?(?:\\s+(\\d{1,2})\\s+$MINUTES)?"
            (
                c.take("$B(?:о|об|в|у|на|до)\\s+$clock$E")
                    ?: c.take("$B(?:о|об|в|у|на|до)?\\s*$clock(?=\\s+$PERIOD$E)")
            )?.let {
                val h = it.groupValues[1].toInt()
                val m = (it.groups[2]?.value ?: it.groups[3]?.value ?: it.groups[4]?.value)?.toInt() ?: 0
                if (h in 0..23 && m in 0..59) {
                    time = LocalTime.of(h, m)
                    hourIsExplicit12h = h in 1..12
                }
            }
        }
        if (time == null) {
            c.take("$B(\\d{1,2}):(\\d{2})$E")?.let {
                val h = it.groupValues[1].toInt()
                val m = it.groupValues[2].toInt()
                if (h in 0..23 && m in 0..59) time = LocalTime.of(h, m)
            }
        }

        // «ранку», «ввечері» — уточнюють годину або задають її самі.
        var period: String? = null
        val periods = listOf(
            "(?:ранку|вранці|зранку|уранці|утра|утром)" to "morning",
            "(?:дня|вдень|удень|після\\s+обіду|пообіді|днем|днём|после\\s+обеда)" to "day",
            "(?:вечора|ввечері|увечері|звечора|вечера|вечером)" to "evening",
            "(?:ночі|вночі|уночі|ночи|ночью)" to "night",
        )
        for ((p, name) in periods) {
            if (c.take("$B$p$E") != null) {
                period = name
                break
            }
        }
        time = when {
            time == null -> when (period) {
                "morning" -> LocalTime.of(9, 0)
                "day" -> LocalTime.of(13, 0)
                "evening" -> LocalTime.of(20, 0)
                "night" -> LocalTime.of(23, 0)
                else -> null
            }
            !hourIsExplicit12h -> time
            period == "day" && time!!.hour in 1..6 -> time!!.plusHours(12)
            period == "evening" && time!!.hour in 1..11 -> time!!.plusHours(12)
            period == "night" && time!!.hour == 12 -> LocalTime.MIDNIGHT
            period == "night" && time!!.hour in 7..11 -> time!!.plusHours(12)
            period == "morning" && time!!.hour == 12 -> LocalTime.MIDNIGHT
            else -> time
        }

        // ---- День ----
        val today = base.toLocalDate()
        var date: LocalDate? = null
        var dateIsWeekday = false
        var dateWithoutYear = false
        var dateIsDayOfMonth = false
        when {
            c.take("$B(?:на\\s+)?(?:післязавтра|позавтра|послезавтра)$E") != null -> date = today.plusDays(2)
            c.take("$B(?:на\\s+)?завтра$E") != null -> date = today.plusDays(1)
            c.take("$B(?:на\\s+)?(?:сьогодні|сегодня)$E") != null -> date = today
        }
        if (date == null) {
            c.take("$B(?:(?:в|у|во|на)\\s+)?(?:цю\\s+|цей\\s+|эту\\s+|этот\\s+|наступн\\p{L}*\\s+|следующ\\p{L}*\\s+)?$WEEKDAY$E")?.let {
                date = today.with(TemporalAdjusters.nextOrSame(weekday(it.groupValues[1])))
                dateIsWeekday = true
            }
        }
        if (date == null) {
            c.take("$B(?:на\\s+)?(\\d{1,2})(?:-?го)?\\s+$MONTH(?:\\s+(\\d{4}))?$E")?.let {
                date = safeDate(it.groupValues[3].toIntOrNull() ?: today.year, months.getValue(it.groupValues[2]), it.groupValues[1].toInt())
                dateWithoutYear = it.groupValues[3].isEmpty()
            }
        }
        if (date == null) {
            c.take("$B(?:на\\s+)?(\\d{1,2})(?:-?го)?\\s+числа$E")?.let {
                val dom = it.groupValues[1].toInt().coerceIn(1, 31)
                date = today.withDayOfMonth(minOf(dom, today.lengthOfMonth()))
                dateIsDayOfMonth = true
            }
        }
        if (date == null) {
            c.take("$B(?:на\\s+)?(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?$E")?.let {
                val y = it.groupValues[3].toIntOrNull()?.let { v -> if (v < 100) 2000 + v else v }
                date = safeDate(y ?: today.year, it.groupValues[2].toInt(), it.groupValues[1].toInt())
                dateWithoutYear = y == null
            }
        }
        if (date == null && repeatWeekday != null) {
            date = today.with(TemporalAdjusters.nextOrSame(repeatWeekday!!))
            dateIsWeekday = true
        }
        if (date == null && dayOffset != null) date = today.plusDays(dayOffset!!)
        if (date == null && monthOffset != null) date = today.plusMonths(monthOffset!!)

        // ---- Збираємо дату й час ----
        val at: LocalDateTime? = when {
            absolute != null -> absolute
            date == null && time == null -> null
            else -> {
                val d = date ?: today
                val t = time ?: if (dayOffset != null || monthOffset != null) base.toLocalTime() else LocalTime.of(9, 0)
                var result = d.atTime(t)
                if (!result.isAfter(base)) {
                    result = when {
                        date == null -> result.plusDays(1)
                        dateIsWeekday -> result.plusWeeks(1)
                        dateWithoutYear -> result.plusYears(1)
                        dateIsDayOfMonth -> result.plusMonths(1)
                        repeat != Repeat.NONE -> nextAfter(result, repeat, base)
                        else -> result
                    }
                }
                result
            }
        }

        return VoiceCommand(
            text = cleanText(c.text),
            at = at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            repeat = repeat,
        )
    }

    private fun nextAfter(start: LocalDateTime, repeat: Repeat, now: LocalDateTime): LocalDateTime {
        var t = start
        while (!t.isAfter(now)) {
            t = when (repeat) {
                Repeat.DAILY -> t.plusDays(1)
                Repeat.WEEKLY -> t.plusWeeks(1)
                Repeat.MONTHLY -> t.plusMonths(1)
                Repeat.YEARLY -> t.plusYears(1)
                Repeat.NONE -> return t
            }
        }
        return t
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try {
            LocalDate.of(y, m, d)
        } catch (_: Exception) {
            null
        }

    private val commandPrefix = Regex(
        "^\\s*(?:будь\\s+ласка,?\\s*)?" +
            "(?:нагадай|нагадайте|нагадати|нагадаю|нагадав|напомни|напомните|напомню|напомніть|поставь|создай|постав|поставте|поставь|створи|створіть|зроби|зробіть|запиши|запишіть|додай|додайте)" +
            "(?:\\s+(?:мені|нам|будь\\s+ласка))?" +
            "(?:\\s+(?:нагадування|нагадувалку|будильник|напоминалку))?$E",
        RegexOption.IGNORE_CASE,
    )
    private val leadingFiller = Regex("^(?:[\\s,.:;!\\-—]+|(?:на|що|щоб|про|те|мені|і|та|й|нагадування)$E)+", RegexOption.IGNORE_CASE)
    private val trailingFiller = Regex("(?:[\\s,.:;\\-—]+|$B(?:на|о|об|в|у|і|та|що|щоб|про)$E)+$", RegexOption.IGNORE_CASE)

    private fun cleanText(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        t = t.replace(commandPrefix, "")
        t = t.replace(leadingFiller, "").replace(trailingFiller, "")
        t = t.replace(Regex("\\s+([,.!?])"), "$1").replace(Regex("\\s+"), " ").trim()
        return t.replaceFirstChar { it.titlecase() }
    }
}

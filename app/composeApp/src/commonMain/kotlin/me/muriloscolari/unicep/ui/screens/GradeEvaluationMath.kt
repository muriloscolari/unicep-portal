package me.muriloscolari.unicep.ui.screens

import me.muriloscolari.unicep.data.api.PartialGradeItem
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.ReportCardResponse
import kotlin.math.abs
import kotlin.math.roundToInt

data class UnifiedSubject(
    val code: String,
    val name: String,
    val grade: PartialGradeItem?,
    val report: ReportCardItem?
)

fun mergeUnifiedSubjects(
    grades: List<PartialGradeItem>,
    report: ReportCardResponse
): List<UnifiedSubject> {
    val gradeMap = grades.associateBy { it.code }
    val reportMap = report.items.associateBy { it.code }
    val orderedCodes = (grades.map { it.code } + report.items.map { it.code }).distinct()
    return orderedCodes.map { code ->
        val g = gradeMap[code]
        val r = reportMap[code]
        UnifiedSubject(
            code = code,
            name = g?.name?.takeIf { it.isNotBlank() } ?: r?.name.orEmpty(),
            grade = g,
            report = r
        )
    }
}

fun ReportCardItem.isFrequencyAtLimit(): Boolean = hours > 0 && remainingAbsences <= 3

internal fun UnifiedSubject.numericAverage(): Double? =
    grade?.average?.value ?: report?.finalAverageValue

internal fun UnifiedSubject.isAtRisk(): Boolean {
    val freqRisk = report?.isFrequencyAtLimit() == true
    val avg = numericAverage()
    val gradeRisk = grade?.belowPassing == true || (avg != null && avg < 6.0)
    return freqRisk || gradeRisk
}

internal data class EvalSlot(
    val label: String,
    val gradeDisplay: String,
    val gradeValue: Double?,
    val weightDisplay: String,
    val weightValue: Double?
)

internal fun buildEvalSlots(item: PartialGradeItem?): List<EvalSlot> {
    if (item == null) return emptyList()
    return buildList {
        for (i in 0 until 4) {
            val g = item.exams.getOrNull(i)
            val w = item.examWeights.getOrNull(i)
            if (!g?.display.isNullOrBlank() || !w?.display.isNullOrBlank()) {
                add(EvalSlot("P${i + 1}", g?.display.orEmpty(), g?.value, w?.display.orEmpty(), w?.value))
            }
        }
        for (i in 0 until 4) {
            val g = item.works.getOrNull(i)
            val w = item.workWeights.getOrNull(i)
            if (!g?.display.isNullOrBlank() || !w?.display.isNullOrBlank()) {
                add(EvalSlot("T${i + 1}", g?.display.orEmpty(), g?.value, w?.display.orEmpty(), w?.value))
            }
        }
    }
}

internal fun computeNeededGrade(slots: List<EvalSlot>, targetAvg: Double): String? {
    val weightedSlots = slots.filter { (it.weightValue ?: 0.0) > 0.0 }
    if (weightedSlots.isEmpty()) return null

    val totalWeight = weightedSlots.sumOf { it.weightValue ?: 0.0 }
    val earnedPoints = weightedSlots.sumOf { (it.gradeValue ?: 0.0) * (it.weightValue ?: 0.0) }
    val missing = weightedSlots.filter { it.gradeValue == null }
    if (missing.isEmpty()) return null

    val remainingWeight = missing.sumOf { it.weightValue ?: 0.0 }
    val needed = ((targetAvg * totalWeight) - earnedPoints) / remainingWeight
    val rounded = ((needed * 100.0).roundToInt() / 100.0)
    val formatted = rounded.toString().replace('.', ',')
    val targetFmt = formatOneDecimal(targetAvg)
    val labels = missing.joinToString(", ") { it.label }

    return when {
        needed <= 0.0 -> "Você já garantiu média ≥ $targetFmt mesmo tirando 0 em $labels."
        needed > 10.0 -> "Precisaria de $formatted em $labels (acima de 10,0 com os pesos atuais)."
        else -> "Precisa tirar em média $formatted nas avaliações restantes ($labels) para fechar com $targetFmt."
    }
}

internal fun formatOneDecimal(v: Double): String {
    val tenths = (v * 10.0).roundToInt()
    val whole = tenths / 10
    val frac = abs(tenths % 10)
    return "$whole,$frac"
}

internal fun shortenPeriod(label: String): String {
    return label
        .replace("semestre de ", "")
        .replace("º ", "/")
        .trim()
}

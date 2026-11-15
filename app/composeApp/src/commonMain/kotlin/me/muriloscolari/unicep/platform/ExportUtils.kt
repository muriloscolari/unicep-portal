package me.muriloscolari.unicep.platform

import me.muriloscolari.unicep.data.api.PartialGradeItem
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.data.api.StudentInfo

fun buildGradesExportData(
    student: StudentInfo,
    periodLabel: String,
    grades: List<PartialGradeItem>,
    reportItems: List<ReportCardItem>
): ExportCardData {
    val reportByCode = reportItems.associateBy { it.code.trim().uppercase() }
    val reportByName = reportItems.associateBy { it.name.trim().uppercase() }

    val subtitleParts = buildList {
        if (student.name.isNotBlank()) add(student.name)
        if (student.ra.isNotBlank()) add("RA ${student.ra}")
        if (periodLabel.isNotBlank()) add(periodLabel)
    }
    val subtitle = subtitleParts.joinToString(" · ")

    val sections = grades.map { item ->
        val report = reportByCode[item.code.trim().uppercase()] ?: reportByName[item.name.trim().uppercase()]
        val avgText = item.average.display.ifBlank { report?.finalAverage ?: "—" }
        val badgeParts = buildList {
            add("Média $avgText")
            if (report != null && report.hours > 0) {
                add("${report.absences}/${report.maxAbsences} faltas")
            }
        }

        val lines = buildList {
            val evalParts = buildList {
                item.exams.forEachIndexed { idx, cell ->
                    val w = item.examWeights.getOrNull(idx)?.display.orEmpty()
                    if (cell.display.isNotBlank() || w.isNotBlank()) {
                        add("P${idx + 1}: ${cell.display.ifBlank { "—" }}${if (w.isNotBlank()) " ($w)" else ""}")
                    }
                }
                item.works.forEachIndexed { idx, cell ->
                    val w = item.workWeights.getOrNull(idx)?.display.orEmpty()
                    if (cell.display.isNotBlank() || w.isNotBlank()) {
                        add("T${idx + 1}: ${cell.display.ifBlank { "—" }}${if (w.isNotBlank()) " ($w)" else ""}")
                    }
                }
            }
            if (evalParts.isNotEmpty()) add(evalParts.joinToString(" · "))
            if (report != null) {
                val freq = report.freqPercent?.let { "${((it * 10).toInt() / 10.0)}%" } ?: "—"
                val rem = if (report.hours > 0) " · Restam ${report.remainingAbsences} faltas" else ""
                val status = report.statusLabel.ifBlank { report.status }
                add("Freq: $freq$rem${if (status.isNotBlank()) " · $status" else ""}")
            }
        }

        ExportSection(
            heading = item.name,
            badge = badgeParts.joinToString(" · "),
            lines = lines
        )
    }

    val plainText = buildString {
        appendLine("UNICEP Portal — Resumo do Semestre")
        if (subtitle.isNotBlank()) appendLine(subtitle)
        appendLine()
        sections.forEach { sec ->
            appendLine("• ${sec.heading} (${sec.badge})")
            sec.lines.forEach { line -> appendLine("  $line") }
        }
    }.trim()

    return ExportCardData(
        title = "Resumo do Semestre",
        subtitle = subtitle,
        footer = "UNICEP Portal · unicep.muriloscolari.me",
        sections = sections,
        plainText = plainText
    )
}

fun buildScheduleExportData(
    student: StudentInfo,
    schedule: ScheduleResponse
): ExportCardData {
    val subtitleParts = buildList {
        if (student.name.isNotBlank()) add(student.name)
        if (student.ra.isNotBlank()) add("RA ${student.ra}")
    }
    val subtitle = subtitleParts.joinToString(" · ")
    val headers = schedule.headers

    val sections = schedule.rows.mapNotNull { row ->
        if (row.all { it.isBlank() }) return@mapNotNull null
        val heading = row.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Horário"
        val lines = row.drop(1).mapIndexedNotNull { index, cell ->
            val clean = cell.trim()
            if (clean.isBlank() || clean == "-") null
            else {
                val colHeader = headers.getOrNull(index + 1)?.trim().orEmpty()
                if (colHeader.isNotBlank()) "$colHeader: $clean" else clean
            }
        }
        ExportSection(
            heading = heading,
            badge = if (lines.size == 1) "1 aula" else "${lines.size} aulas",
            lines = if (lines.isEmpty()) listOf("Sem aulas registradas") else lines
        )
    }

    val plainText = buildString {
        appendLine("UNICEP Portal — ${schedule.title.ifBlank { "Horário das Aulas" }}")
        if (subtitle.isNotBlank()) appendLine(subtitle)
        appendLine()
        sections.forEach { sec ->
            appendLine("• ${sec.heading}")
            sec.lines.forEach { line -> appendLine("  $line") }
        }
    }.trim()

    return ExportCardData(
        title = schedule.title.ifBlank { "Horário das Aulas" },
        subtitle = subtitle,
        footer = "UNICEP Portal · unicep.muriloscolari.me",
        sections = sections,
        plainText = plainText
    )
}

fun escapeHtml(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

fun buildExportPrintHtml(data: ExportCardData): String = buildString {
    append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
    append("<title>${escapeHtml(data.title)}</title>")
    append("<style>")
    append("*{box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Inter','Segoe UI',sans-serif;margin:28px;color:#111827;background:#fff;}")
    append(".header{border-bottom:2px solid #e5e7eb;padding-bottom:14px;margin-bottom:18px;}")
    append(".title{font-size:22px;font-weight:700;margin:0 0 4px 0;color:#0f172a;}")
    append(".subtitle{font-size:13px;color:#475569;margin:0;}")
    append(".card{border:1px solid #e2e8f0;border-radius:10px;padding:12px 14px;margin-bottom:10px;page-break-inside:avoid;}")
    append(".card-head{display:flex;justify-content:space-between;align-items:baseline;gap:12px;margin-bottom:6px;}")
    append(".card-title{font-size:14px;font-weight:600;color:#0f172a;margin:0;}")
    append(".badge{font-size:12px;font-weight:600;color:#1d4ed8;background:#eff6ff;padding:2px 8px;border-radius:999px;white-space:nowrap;}")
    append(".line{font-size:12.5px;color:#334155;margin:3px 0;line-height:1.4;}")
    append(".footer{margin-top:20px;padding-top:10px;border-top:1px solid #e5e7eb;font-size:11px;color:#64748b;text-align:center;}")
    append("</style></head><body>")
    append("<div class=\"header\"><h1 class=\"title\">${escapeHtml(data.title)}</h1>")
    if (data.subtitle.isNotBlank()) {
        append("<p class=\"subtitle\">${escapeHtml(data.subtitle)}</p>")
    }
    append("</div>")
    data.sections.forEach { sec ->
        append("<div class=\"card\"><div class=\"card-head\">")
        append("<h2 class=\"card-title\">${escapeHtml(sec.heading)}</h2>")
        if (sec.badge.isNotBlank()) {
            append("<span class=\"badge\">${escapeHtml(sec.badge)}</span>")
        }
        append("</div>")
        sec.lines.forEach { line ->
            append("<div class=\"line\">${escapeHtml(line)}</div>")
        }
        append("</div>")
    }
    if (data.footer.isNotBlank()) {
        append("<div class=\"footer\">${escapeHtml(data.footer)}</div>")
    }
    append("</body></html>")
}

package me.muriloscolari.unicep.data.api

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val ra: String,
    val password: String,
    val perfil: String = "alunograd"
)

@Immutable
@Serializable
data class StudentInfo(
    val ra: String = "",
    val name: String = "",
    val campus: String = "",
    val perfil: String = "alunograd",
    val semester: String = ""
)

@Immutable
@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: String,
    val student: StudentInfo,
    val nacOpen: Boolean = false
)

@Immutable
@Serializable
data class PeriodOption(
    val id: String,
    val label: String,
    val query: String = ""
)

@Immutable
@Serializable
data class GradeCell(
    val display: String = "",
    val value: Double? = null
)

@Immutable
@Serializable
data class PartialGradeItem(
    val code: String,
    val name: String,
    val exams: List<GradeCell> = emptyList(),
    val works: List<GradeCell> = emptyList(),
    val examWeights: List<GradeCell> = emptyList(),
    val workWeights: List<GradeCell> = emptyList(),
    val average: GradeCell = GradeCell(),
    val belowPassing: Boolean = false
)

@Immutable
@Serializable
data class GradesResponse(
    val period: String = "",
    val items: List<PartialGradeItem> = emptyList()
)

@Immutable
@Serializable
data class ReportCardItem(
    val code: String,
    val name: String,
    val updatedAt: String = "",
    val classGroup: String = "",
    val finalAverage: String = "",
    val finalAverageValue: Double? = null,
    val hours: Int = 0,
    val absences: Int = 0,
    val maxAbsences: Int = 0,
    val remainingAbsences: Int = 0,
    val freqPercent: Double? = null,
    val status: String = "",
    val statusLabel: String = ""
)

@Immutable
@Serializable
data class ReportCardResponse(
    val periodQuery: String = "",
    val items: List<ReportCardItem> = emptyList(),
    val semesterComplementaryHours: String = "",
    val totalComplementaryHours: String = ""
)

@Immutable
@Serializable
data class LinkItem(
    val title: String,
    val path: String,
    val category: String = "",
    val proxy: Boolean = false
)

@Immutable
@Serializable
data class DashboardSummary(
    val greeting: String = "",
    val notices: List<String> = emptyList(),
    val enrollmentLinks: List<LinkItem> = emptyList(),
    val gradeId: String = "",
    val nacOpen: Boolean = false
)

@Immutable
@Serializable
data class NotificationItem(
    val id: Long,
    val ra: String,
    val periodKey: String,
    val disciplineCode: String,
    val disciplineName: String,
    val field: String,
    val oldValue: String = "",
    val newValue: String,
    val kind: String,
    val createdAt: String,
    val readAt: String? = null
)

@Immutable
@Serializable
data class NotificationsResponse(
    val items: List<NotificationItem> = emptyList()
)

@Immutable
@Serializable
data class DashboardResponse(
    val student: StudentInfo = StudentInfo(),
    val summary: DashboardSummary = DashboardSummary(),
    val gradePeriods: List<PeriodOption> = emptyList(),
    val reportPeriods: List<PeriodOption> = emptyList(),
    val currentGrades: List<PartialGradeItem> = emptyList(),
    val currentReport: ReportCardResponse = ReportCardResponse(),
    val unreadNotifications: List<NotificationItem> = emptyList()
)

@Immutable
@Serializable
data class TranscriptItem(
    val name: String,
    val yearSem: String = "",
    val exam: String = "",
    val average: String = "",
    val hours: String = "",
    val absences: String = "",
    val freqPercent: String = "",
    val status: String = "",
    val statusLabel: String = "",
    val syllabusUrl: String = ""
)

@Immutable
@Serializable
data class CurriculumItem(
    val period: String = "",
    val code: String,
    val name: String,
    val hours: String = "",
    val prerequisite: String = ""
)

@Immutable
@Serializable
data class CurriculumResponse(
    val transcript: List<TranscriptItem> = emptyList(),
    val matrix: List<CurriculumItem> = emptyList(),
    val gradeId: String = ""
)

@Immutable
@Serializable
data class ScheduleResponse(
    val title: String = "Horário das Aulas",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

@Immutable
@Serializable
data class TuitionItem(
    val ra: String,
    val installment: String,
    val dueDate: String,
    val status: String,
    val grossAmount: String,
    val discountAmount: String
)

@Immutable
@Serializable
data class FinanceResponse(
    val installments: List<TuitionItem> = emptyList(),
    val links: List<LinkItem> = emptyList()
)

@Immutable
@Serializable
data class ServicesResponse(
    val shortcuts: List<LinkItem> = emptyList(),
    val institutionalDocs: List<LinkItem> = emptyList()
)

@Serializable
data class AvaSsoResponse(val url: String)

@Serializable
data class SyncResponse(val created: Int = 0)

@Serializable
data class ErrorResponse(
    val error: String = "Erro desconhecido",
    val code: String = ""
)

@Serializable
data class VirtualSettingsPayload(
    val pollingEnabled: Boolean = true
)

@Serializable
data class MarkReadRequest(val ids: List<Long> = emptyList())

@Serializable
data class ProxyTicketRequest(val path: String)

@Serializable
data class ProxyTicketResponse(
    val ticket: String,
    val expiresIn: Int = 60
)

// --- Sala Virtual (D2L Brightspace - Passivo / Somente Leitura) ---

@Immutable
@Serializable
data class VirtualSemesterOption(
    val id: String = "",
    val name: String = "",
    val code: String = ""
)

@Immutable
@Serializable
data class VirtualCourseSummary(
    val orgUnitId: Long,
    val name: String = "",
    val code: String = "",
    val semesterName: String = "",
    val isActive: Boolean = false,
    val canAccess: Boolean = true,
    val startDate: String = "",
    val endDate: String = "",
    val lastAccessed: String = "",
    val homeUrl: String = "",
    val pendingCount: Int = 0,
    val assignmentCount: Int = 0
)

@Immutable
@Serializable
data class VirtualFileAttachment(
    val fileId: Long = 0L,
    val fileName: String = "",
    val size: Long = 0L,
    val downloadPath: String = ""
)

@Immutable
@Serializable
data class VirtualAnnouncement(
    val id: Long = 0L,
    val orgUnitId: Long = 0L,
    val orgUnitName: String = "",
    val title: String = "",
    val body: String = "",
    val createdDate: String = "",
    val startDate: String = "",
    val attachments: List<VirtualFileAttachment> = emptyList()
)

@Immutable
@Serializable
data class VirtualCalendarEvent(
    val id: Long = 0L,
    val orgUnitId: Long = 0L,
    val orgUnitName: String = "",
    val title: String = "",
    val description: String = "",
    val startDateTime: String = "",
    val endDateTime: String = "",
    val allDay: Boolean = false,
    val webUrl: String = ""
)

@Immutable
@Serializable
data class VirtualAssignmentItem(
    val id: Long = 0L,
    val orgUnitId: Long = 0L,
    val orgUnitName: String = "",
    val name: String = "",
    val instructions: String = "",
    val dueDate: String = "",
    val dueAtMillis: Long = 0L,
    val startDate: String = "",
    val endDate: String = "",
    val outOf: Double? = null,
    val attachments: List<VirtualFileAttachment> = emptyList(),
    val status: String = "not_submitted",
    val statusLabel: String = "Não enviado",
    val submissionDate: String = "",
    val submittedFiles: List<VirtualFileAttachment> = emptyList(),
    val score: Double? = null,
    val feedbackText: String = "",
    val completionDate: String = "",
    val webUrl: String = ""
)

@Immutable
@Serializable
data class VirtualContentTopic(
    val topicId: Long = 0L,
    val title: String = "",
    val typeIdentifier: String = "",
    val url: String = "",
    val downloadPath: String = "",
    val externalUrl: String = "",
    val unread: Boolean = false,
    val lastModifiedDate: String = ""
)

@Immutable
@Serializable
data class VirtualContentModule(
    val moduleId: Long = 0L,
    val title: String = "",
    val description: String = "",
    val topics: List<VirtualContentTopic> = emptyList(),
    val modules: List<VirtualContentModule> = emptyList()
)

@Immutable
@Serializable
data class VirtualGradeItem(
    val id: String = "",
    val name: String = "",
    val displayedGrade: String = "",
    val pointsNumerator: Double? = null,
    val pointsDenominator: Double? = null,
    val comments: String = "",
    val lastModified: String = "",
    val releasedDate: String = ""
)

@Immutable
@Serializable
data class VirtualQuizItem(
    val quizId: Long = 0L,
    val orgUnitId: Long = 0L,
    val orgUnitName: String = "",
    val name: String = "",
    val description: String = "",
    val isActive: Boolean = false,
    val startDate: String = "",
    val endDate: String = "",
    val dueDate: String = "",
    val timeLimitMins: Int? = null,
    val attemptsAllowed: Int? = null,
    val webUrl: String = ""
)

@Immutable
@Serializable
data class VirtualDiscussionForum(
    val forumId: Long = 0L,
    val name: String = "",
    val description: String = "",
    val webUrl: String = ""
)

@Immutable
@Serializable
data class VirtualOverviewResponse(
    val baseUrl: String = "https://salavirtual.unicep.edu.br",
    val userId: String = "",
    val userName: String = "",
    val complete: Boolean = true,
    val semesters: List<VirtualSemesterOption> = emptyList(),
    val courses: List<VirtualCourseSummary> = emptyList(),
    val upcomingAssignments: List<VirtualAssignmentItem> = emptyList(),
    val upcomingQuizzes: List<VirtualQuizItem> = emptyList(),
    val announcements: List<VirtualAnnouncement> = emptyList(),
    val calendarEvents: List<VirtualCalendarEvent> = emptyList()
)

@Immutable
@Serializable
data class VirtualCourseDetailResponse(
    val baseUrl: String = "https://salavirtual.unicep.edu.br",
    val course: VirtualCourseSummary,
    val announcements: List<VirtualAnnouncement> = emptyList(),
    val modules: List<VirtualContentModule> = emptyList(),
    val assignments: List<VirtualAssignmentItem> = emptyList(),
    val grades: List<VirtualGradeItem> = emptyList(),
    val quizzes: List<VirtualQuizItem> = emptyList(),
    val discussions: List<VirtualDiscussionForum> = emptyList(),
    val calendar: List<VirtualCalendarEvent> = emptyList()
)

// --- Calendário Pessoal ---

@Immutable
@Serializable
data class UserCalendarEvent(
    val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val time: String = "",
    val allDay: Boolean = false,
    val startAtMillis: Long = 0L,
    val disciplineName: String = "",
    val colorKey: String = "accent",
    val reminderMinutes: List<Int> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Immutable
@Serializable
data class CalendarEventInput(
    val title: String,
    val description: String = "",
    val date: String,
    val time: String = "",
    val allDay: Boolean = false,
    val startAtMillis: Long = 0L,
    val disciplineName: String = "",
    val colorKey: String = "accent",
    val reminderMinutes: List<Int> = emptyList()
)

@Serializable
data class CalendarEventsResponse(
    val events: List<UserCalendarEvent> = emptyList()
)

@Serializable
data class CalendarEventSingleResponse(
    val event: UserCalendarEvent
)

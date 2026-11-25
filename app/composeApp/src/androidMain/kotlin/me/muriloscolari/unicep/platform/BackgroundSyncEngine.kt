package me.muriloscolari.unicep.platform

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.LoginRequest
import me.muriloscolari.unicep.data.api.PortalApiClient
import me.muriloscolari.unicep.data.api.UnauthorizedException
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.repo.BG_FULL_SYNC_INTERVAL_MS
import me.muriloscolari.unicep.data.repo.PrefKeys
import me.muriloscolari.unicep.data.repo.evaluateCalendarReminders
import me.muriloscolari.unicep.data.repo.evaluateFrequencyAlerts
import me.muriloscolari.unicep.data.repo.evaluateVirtualDeadlineAlerts
import me.muriloscolari.unicep.data.repo.isQuietHoursForFullSync
import me.muriloscolari.unicep.data.repo.readFrequencyAlertThreshold
import me.muriloscolari.unicep.data.repo.readVirtualDeadlineLeadHours
import me.muriloscolari.unicep.data.repo.readVirtualNotifyNewAssignment

internal enum class SyncOutcome {
    SUCCESS,
    NO_SESSION,
    RETRY_NEEDED
}

internal object BackgroundSyncEngine {
    private val syncMutex = Mutex()
    private val calendarLock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun isFullSyncDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = PlatformBridge.getString(PrefKeys.LAST_BG_FULL_SYNC_MS)?.toLongOrNull() ?: 0L
        return nowMillis - last >= BG_FULL_SYNC_INTERVAL_MS
    }

    suspend fun sync(context: Context): SyncOutcome = syncMutex.withLock {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)

        val server = PlatformBridge.getString(PrefKeys.SERVER_URL)
            ?.takeIf { it.isNotBlank() }
            ?: return@withLock SyncOutcome.NO_SESSION
        var token = PlatformBridge.getString(PrefKeys.JWT_TOKEN)
            ?.takeIf { it.isNotBlank() }
            ?: return@withLock SyncOutcome.NO_SESSION

        val api = PortalApiClient(
            connectTimeoutMs = 10_000L,
            requestTimeoutMs = 25_000L,
            socketTimeoutMs = 25_000L
        )

        try {
            val unread = try {
                api.notifications(server, token, unreadOnly = true).items
            } catch (_: UnauthorizedException) {
                val ra = PlatformBridge.getString(PrefKeys.RA) ?: return@withLock SyncOutcome.NO_SESSION
                val pass = PlatformBridge.getString(PrefKeys.PASSWORD) ?: return@withLock SyncOutcome.NO_SESSION
                val perfil = PlatformBridge.getString(PrefKeys.PERFIL) ?: "alunograd"
                val fresh = runCatching { api.login(server, LoginRequest(ra, pass, perfil)) }.getOrNull()
                    ?: return@withLock SyncOutcome.RETRY_NEEDED
                token = fresh.token
                PlatformBridge.putStringSync(PrefKeys.JWT_TOKEN, token)
                runCatching { api.notifications(server, token, unreadOnly = true).items }
                    .getOrElse { return@withLock SyncOutcome.RETRY_NEEDED }
            } catch (_: Exception) {
                return@withLock SyncOutcome.RETRY_NEEDED
            }

            val lastSeenId = PlatformBridge.getString(PrefKeys.LAST_NOTIFIED_ID)?.toLongOrNull() ?: 0L
            val freshOnes = unread.filter { it.id > lastSeenId }.sortedBy { it.id }
            val notifyVirtualNew = readVirtualNotifyNewAssignment()
            if (freshOnes.isNotEmpty()) {
                var maxProcessedId = lastSeenId
                var anyHandled = false
                freshOnes.forEach { n ->
                    val isD2L = n.kind == "d2l_assignment" ||
                        n.kind == "d2l_announcement" ||
                        n.kind == "d2l_content" ||
                        n.kind == "d2l_quiz"
                    if (isD2L) {
                        if (!notifyVirtualNew) {
                            maxProcessedId = maxOf(maxProcessedId, n.id)
                            anyHandled = true
                            return@forEach
                        }
                        val prefix = when (n.kind) {
                            "d2l_announcement" -> "Novo aviso"
                            "d2l_content" -> "Novo material"
                            "d2l_quiz" -> "Novo questionário"
                            else -> "Nova atividade"
                        }
                        val title = "$prefix: ${n.disciplineName.ifBlank { "Sala Virtual" }}"
                        val body = if (n.newValue.isNotBlank()) "${n.field} • ${n.newValue}" else n.field
                        if (GradeSyncWorker.showLocalNotification(appCtx, n.id.toInt(), title, body, openTab = AppTabIndex.VIRTUAL)) {
                            maxProcessedId = maxOf(maxProcessedId, n.id)
                            anyHandled = true
                        }
                        return@forEach
                    }
                    val title = if (n.kind == "grade") "Nova nota: ${n.disciplineName}" else "Atualização: ${n.disciplineName}"
                    val oldTxt = if (n.oldValue.isBlank()) "—" else n.oldValue
                    val body = "${n.field}: $oldTxt → ${n.newValue}"
                    if (GradeSyncWorker.showLocalNotification(appCtx, n.id.toInt(), title, body, openTab = AppTabIndex.CENTRAL)) {
                        maxProcessedId = maxOf(maxProcessedId, n.id)
                        anyHandled = true
                    }
                }
                if (anyHandled) {
                    PlatformBridge.putStringSync(PrefKeys.LAST_NOTIFIED_ID, maxProcessedId.toString())
                }
            }

            evaluateCachedCalendarReminders(appCtx)

            val localNow = PlatformBridge.currentLocalDateTime()
            if (isFullSyncDue() && !isQuietHoursForFullSync(localNow.hour)) {
                val freqThreshold = readFrequencyAlertThreshold()
                if (freqThreshold > 0) {
                    runCatching { api.dashboard(server, token) }.getOrNull()?.let { dash ->
                        evaluateFrequencyAlerts(dash.currentReport.items, freqThreshold)
                    }
                }

                val leadHours = readVirtualDeadlineLeadHours()
                if (leadHours.isNotEmpty()) {
                    runCatching { api.virtualOverview(server, token) }.getOrNull()?.let { ov ->
                        if (!PlatformBridge.getString(PrefKeys.CACHE_OWNER_RA).isNullOrBlank()) {
                            runCatching {
                                PlatformBridge.putStringSync(PrefKeys.CACHE_VIRTUAL_OVERVIEW, json.encodeToString(ov))
                            }
                        }
                        evaluateVirtualDeadlineAlerts(ov.upcomingAssignments, leadHours)
                    }
                }

                runCatching { api.calendarEvents(server, token).events }.getOrNull()?.let { events ->
                    runCatching {
                        PlatformBridge.putStringSync(PrefKeys.CACHE_CALENDAR_EVENTS, json.encodeToString(events))
                    }
                    PlatformBridge.syncCalendarReminders(events)
                    evaluateCalendarReminders(
                        events = events,
                        utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes
                    )
                }

                PlatformBridge.putStringSync(PrefKeys.LAST_BG_FULL_SYNC_MS, System.currentTimeMillis().toString())
            }

            return@withLock SyncOutcome.SUCCESS
        } finally {
            runCatching { api.close() }
        }
    }

    fun evaluateCachedCalendarReminders(context: Context) {
        PlatformBridge.initContext(context.applicationContext)
        val raw = PlatformBridge.getString(PrefKeys.CACHE_CALENDAR_EVENTS) ?: return
        val events = runCatching { json.decodeFromString<List<UserCalendarEvent>>(raw) }.getOrNull() ?: return
        if (events.isEmpty()) return
        evaluateCalendarReminders(
            events = events,
            utcOffsetMinutes = PlatformBridge.currentLocalDateTime().utcOffsetMinutes,
            storagePut = { k, v -> PlatformBridge.putStringSync(k, v) }
        )
    }

    fun deliverCalendarReminderIfNeeded(
        context: Context,
        reminderKey: String,
        notifId: Int,
        title: String,
        body: String
    ): Boolean = synchronized(calendarLock) {
        val appCtx = context.applicationContext
        PlatformBridge.initContext(appCtx)
        if (reminderKey.isBlank() || title.isBlank()) return false

        val prevMap: MutableMap<String, Boolean> = PlatformBridge.getString(PrefKeys.CALENDAR_REMINDERS_NOTIFIED)
            ?.let { raw -> runCatching { json.decodeFromString<Map<String, Boolean>>(raw) }.getOrNull() }
            ?.toMutableMap()
            ?: mutableMapOf()

        if (prevMap[reminderKey] == true) {
            return false
        }

        val posted = GradeSyncWorker.showCalendarNotification(appCtx, notifId, title, body)
        if (posted) {
            prevMap[reminderKey] = true
            PlatformBridge.putStringSync(PrefKeys.CALENDAR_REMINDERS_NOTIFIED, json.encodeToString(prevMap))
        }
        return posted
    }
}

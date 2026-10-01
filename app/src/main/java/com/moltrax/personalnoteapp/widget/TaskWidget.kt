package com.moltrax.personalnoteapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.model.TaskJson
import com.moltrax.personalnoteapp.data.remote.drive.model.toDomain
import com.moltrax.personalnoteapp.data.remote.drive.model.toJson
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.withCompletion
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import com.moltrax.personalnoteapp.ui.theme.AppColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Sabit boyut ızgaraları yerine SizeMode.Exact: launcher widget'ın GERÇEK boyutunu verir,
    // böylece 3x3'ten 4x4/4x5'e kadar her boyuta sürekli (adaptive) uyum sağlarız.
    override val sizeMode = SizeMode.Exact

    /**
     * Glance widget'ı bir Hilt bileşeni olmadığı için bağımlılıkları application context üzerinden
     * EntryPoint ile alıyoruz.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskWidgetEntryPoint {
        fun taskRepository(): TaskRepository
        fun syncRepository(): SyncRepository
        fun notificationService(): NotificationService
        fun appPreferences(): AppPreferences
    }

    /** Widget'taki tek bir alt görev (checklist) satırı; tıklanınca tamamlanma durumu değişir. */
    private data class SubItem(val id: String, val title: String, val isDone: Boolean)

    private data class TaskItem(
        val id: String,
        val title: String,
        val notes: String?,
        // Spora/programa linkliyse true: tik atınca direkt tamamlanmaz, uygulamada set/ağırlık ekranı açılır.
        val isWorkout: Boolean,
        // Görevin altındaki kontrol-listesi maddeleri (gömülü), widget'ta da listelenir.
        val subtasks: List<SubItem>,
    )

    /** Widget'ta gösterilecek, seçili dile göre çözülmüş sabit metinler (widget Compose değil). */
    private data class WidgetStrings(val title: String, val error: String, val empty: String, val undo: String)

    private sealed interface UiState {
        data class Content(val tasks: List<TaskItem>) : UiState
        data object Error : UiState
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Veriyi composition'dan önce, hata yakalayarak yüklüyoruz; böylece widget asla sonsuz
        // loading'de kalmaz, en kötü ihtimalle "Hata oluştu" gösterir.
        val state = loadState(context)
        // 'Ayarlar' butonunun bu spesifik widget için yapılandırma ekranını açabilmesi adına
        // glanceId'den appWidgetId'yi çözüyoruz.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // Seçili dile göre metinleri çöz (widget LocalContext sağlayıcısını kullanamaz).
        val lang = runCatching { entryPoint(context).appPreferences().language.first() }.getOrDefault("tr")
        val lc = context.localizedFor(lang)
        val strings = WidgetStrings(
            title = lc.getString(R.string.home_title),
            error = lc.getString(R.string.widget_error),
            empty = lc.getString(R.string.widget_empty),
            undo = lc.getString(R.string.undo),
        )
        provideContent {
            GlanceTheme { WidgetRoot(context, state, appWidgetId, strings) }
        }
    }

    private suspend fun loadState(context: Context): UiState =
        runCatching {
            val items = entryPoint(context).taskRepository().observeAll().first()
                .filter { !it.isDone }
                .map { task ->
                    TaskItem(
                        id = task.id,
                        title = task.title,
                        notes = task.notes,
                        isWorkout = task.linkedWorkoutId != null || task.linkedProgramId != null,
                        subtasks = task.subtasks.map { SubItem(it.id, it.title, it.isDone) },
                    )
                }
            UiState.Content(items)
        }.getOrElse { UiState.Error }

    @Composable
    private fun WidgetRoot(context: Context, state: UiState, appWidgetId: Int, strings: WidgetStrings) {
        val size = LocalSize.current
        // Yalnızca gerçekten küçük yerleşimlerde tek görev göster; bunun dışındaki her boyut
        // (orta, büyük, 4x4, 4x5...) tam, kaydırılabilir listeye uyum sağlar.
        val compact = size.width < COMPACT_WIDTH || size.height < COMPACT_HEIGHT
        val prefs = currentState<Preferences>()
        val selectedIds = prefs[SELECTED_TASK_IDS]
        // Son tamamlanan görevin başlığı (geri al şeridi için); yoksa şerit gösterilmez.
        val undoTitle = prefs[UNDO_TASK_TITLE]

        // Dış kapsül: koyu zemin + yumuşak köşeler (modern, kart hissi veren karanlık/neon tema).
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(WidgetColors.Bg))
                .cornerRadius(24.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Header(context, appWidgetId, strings.title)
            Spacer(GlanceModifier.height(10.dp))

            // İçerik kalan alanı kaplar; geri al şeridi varsa altta sabit kalır (liste kaymaz).
            Box(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                when (state) {
                    is UiState.Error -> Message(strings.error, AppColors.Error)
                    is UiState.Content -> {
                        // Bu widget için seçilmiş görev id'leri varsa SADECE onları göster; yoksa
                        // (varsayılan davranış) tüm görevleri göster. Filtre tüm boyutlarda uygulanır.
                        val visible =
                            if (selectedIds.isNullOrEmpty()) state.tasks
                            else state.tasks.filter { it.id in selectedIds }
                        when {
                            visible.isEmpty() -> Message(strings.empty, AppColors.TextSecondary)
                            compact -> CompactList(context, visible, strings.empty)
                            else -> FullList(context, visible)   // büyük/ekstra büyük: tümü, kaydırılabilir
                        }
                    }
                }
            }

            // Geri al şeridi: son tamamlanan görevi tek dokunuşla geri yükler. Tamamlama yapıldığında
            // belirir, "Geri Al"/"✕" ile ya da yenilemede kaybolur.
            if (!undoTitle.isNullOrBlank()) {
                Spacer(GlanceModifier.height(8.dp))
                UndoBar(undoTitle, strings.undo)
            }
        }
    }

    /**
     * Alt geri al (undo) şeridi: solda "✓ <başlık>", sağda neon "Geri Al" butonu ve sade "✕" kapat
     * çipi. Geri Al [UndoTaskAction]'ı, ✕ ise [DismissUndoAction]'ı çalıştırır.
     */
    @Composable
    private fun UndoBar(title: String, undoLabel: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .cornerRadius(12.dp)
                .background(ColorProvider(WidgetColors.Chip))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓ $title",
                style = TextStyle(color = ColorProvider(AppColors.TextSecondary), fontSize = 12.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Box(
                modifier = GlanceModifier.cornerRadius(8.dp)
                    .background(ColorProvider(AppColors.Accent))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .clickable(actionRunCallback<UndoTaskAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = undoLabel,
                    style = TextStyle(
                        color = ColorProvider(AppColors.TextPrimary),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier.size(28.dp).cornerRadius(8.dp)
                    .background(ColorProvider(WidgetColors.Card))
                    .clickable(actionRunCallback<DismissUndoAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = TextStyle(color = ColorProvider(AppColors.TextSecondary), fontSize = 13.sp),
                )
            }
        }
    }

    /**
     * Başlık şeridi: solda "Görevlerim", sağda eşit boyutlu üç ikon butonu. Tüm öğeler sabit
     * yükseklikteki ([HEADER_HEIGHT]) Row içinde dikey ortalanır; başlık [defaultWeight] ile kalan
     * alanı kaplar, böylece butonlar her widget genişliğinde sağ kenara hizalı ve eşit aralıklı kalır.
     */
    @Composable
    private fun Header(context: Context, appWidgetId: Int, title: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(AppColors.TextPrimary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            // Buton grubu: Ayarlar (çark), Yenile (döngüsel ok), Ekle (+). Aralarında eşit boşluk.
            IconButton("⚙", actionStartActivity(configIntent(context, appWidgetId)))
            Spacer(GlanceModifier.width(BTN_GAP))
            IconButton("↻", actionRunCallback<RefreshTaskWidgetAction>())
            Spacer(GlanceModifier.width(BTN_GAP))
            IconButton("＋", actionStartActivity(newTaskIntent(context)), filled = true)
        }
    }

    /**
     * Başlıktaki ikon butonu — eşkenar, yuvarlatılmış kare çip. [filled] true ise neon dolgulu
     * (vurgu) buton ('+' için); diğerleri sade çip zeminli neon sembol. Hepsi aynı [BTN_SIZE]
     * olduğundan başlıkta kusursuz hizalanır.
     */
    @Composable
    private fun IconButton(glyph: String, onClick: androidx.glance.action.Action, filled: Boolean = false) {
        Box(
            modifier = GlanceModifier.size(BTN_SIZE).cornerRadius(10.dp)
                .background(ColorProvider(if (filled) AppColors.Accent else WidgetColors.Chip))
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                style = TextStyle(
                    color = ColorProvider(if (filled) AppColors.TextPrimary else AppColors.Accent),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @Composable
    private fun FullList(context: Context, tasks: List<TaskItem>) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(tasks) { item ->
                Box(modifier = GlanceModifier.padding(bottom = 8.dp)) { TaskRow(context, item) }
            }
        }
    }

    @Composable
    private fun CompactList(context: Context, tasks: List<TaskItem>, emptyText: String) {
        // Çok küçük yerleşimde yalnızca (filtrelenmiş listenin) ilk görevini göster.
        val item = tasks.firstOrNull()
        if (item == null) Message(emptyText, AppColors.TextSecondary)
        else TaskRow(context, item)
    }

    /**
     * Kart tarzı görev satırı: tıklanabilir checkbox + kalın başlık + altında soluk not. Tüm öğeler
     * tek bir Row içinde dikey ortalanır; checkbox solda sabit, metin sütunu kalan alanı [defaultWeight]
     * ile kaplar. Böylece başlık ve checkbox her zaman aynı eksende hizalı kalır (sabit yükseklikli
     * dekoratif çubuk kaldırıldı — asıl hizasızlık kaynağıydı). Koyu kart zemini + yumuşak köşeler.
     */
    @Composable
    private fun TaskRow(context: Context, item: TaskItem) {
        // Spora linkli görevde tik direkt tamamlamaz: uygulamada set/tekrar/ağırlık ekranını açar.
        // Diğer görevlerde her zamanki gibi widget'tan anında tamamlanır.
        val checkAction: androidx.glance.action.Action =
            if (item.isWorkout) actionStartActivity(workoutCompleteIntent(context, item.id))
            else actionRunCallback<CompleteTaskAction>(
                actionParametersOf(CompleteTaskAction.taskIdKey to item.id),
            )
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .cornerRadius(14.dp)
                .background(ColorProvider(WidgetColors.Card))
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(
                checked = false,
                onCheckedChange = checkAction,
            )
            Column(modifier = GlanceModifier.defaultWeight().padding(vertical = 10.dp)) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        color = ColorProvider(AppColors.TextPrimary),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                val note = item.notes
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = TextStyle(
                            color = ColorProvider(AppColors.TextSecondary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        maxLines = 2,
                        modifier = GlanceModifier.padding(top = 3.dp),
                    )
                }
                // Alt görevler (checklist): başlığın/notun altında soluk, küçük satırlar halinde
                // listelenir. Tamamlanmış olanlar üstü çizili. Her satır tıklanabilir: uygulamayı
                // açmadan o alt görevi anında tamamlar/geri alır.
                item.subtasks.forEach { sub -> SubtaskRow(item.id, sub) }
            }
        }
    }

    /**
     * Tek bir alt görev satırı: durum işareti (✓/○) + başlık (tamamlıysa üstü çizili). Tüm satır
     * tıklanabilir; [ToggleSubtaskAction] ile uygulamayı açmadan alt görevi anında tamamlar/geri alır.
     */
    @Composable
    private fun SubtaskRow(taskId: String, sub: SubItem) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 3.dp)
                .clickable(
                    actionRunCallback<ToggleSubtaskAction>(
                        actionParametersOf(
                            ToggleSubtaskAction.taskIdKey to taskId,
                            ToggleSubtaskAction.subtaskIdKey to sub.id,
                        ),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (sub.isDone) "✓" else "○",
                style = TextStyle(
                    color = ColorProvider(if (sub.isDone) AppColors.Accent else AppColors.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = sub.title,
                style = TextStyle(
                    color = ColorProvider(AppColors.TextSecondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    @Composable
    private fun Message(text: String, color: androidx.compose.ui.graphics.Color) {
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(color)),
            modifier = GlanceModifier.padding(12.dp),
        )
    }

    /** Widget'a özel ek tonlar (genel [AppColors] paletinin koyu/neon tamamlayıcıları). */
    private object WidgetColors {
        val Bg   = androidx.compose.ui.graphics.Color(0xFF0B0B12) // dış kapsül zemini
        val Card = androidx.compose.ui.graphics.Color(0xFF1B1B26) // görev kartı zemini
        val Chip = androidx.compose.ui.graphics.Color(0xFF22222E) // sade ikon buton zemini
    }

    companion object {
        // Bu widget örneğinde gösterilecek görevlerin id kümesi (çoklu seçim). Boş/yoksa = tümü.
        val SELECTED_TASK_IDS = stringSetPreferencesKey("selected_task_ids")

        // Son tamamlanan görevin geri al (undo) anlık görüntüsü — yalnızca tıklanan widget örneğinde
        // tutulur. JSON tamamlamadan ÖNCEKİ görevi taşır; başlık geri al şeridinde gösterilir.
        val UNDO_TASK_TITLE = stringPreferencesKey("undo_task_title")
        val UNDO_TASK_JSON = stringPreferencesKey("undo_task_json")

        private val json = Json { ignoreUnknownKeys = true }

        // Tek-görev (kompakt) görünümün altına inilen eşikler; bunun üstü tam listeye geçer.
        private val COMPACT_WIDTH = 200.dp
        private val COMPACT_HEIGHT = 140.dp

        // Başlık şeridi ölçüleri — butonlar ve başlık bu sabit yükseklikte dikey ortalanır.
        private val HEADER_HEIGHT = 40.dp
        private val BTN_SIZE = 38.dp
        private val BTN_GAP = 6.dp

        fun entryPoint(context: Context): TaskWidgetEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                TaskWidgetEntryPoint::class.java,
            )

        private fun newTaskIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("personalnoteapp://new_task")
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_NEW_TASK)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        /**
         * 'Ayarlar' (çark) butonu: bu widget'ın görev seçme ekranını açar. Ana uygulamayı
         * (MainActivity) DEĞİL, kendi task'ında açılan saydam [TaskWidgetConfigActivity]'yi başlatır.
         */
        private fun configIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, TaskWidgetConfigActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // Her widget için benzersiz data → PendingIntent'lerin birbirine karışmaması için.
                data = Uri.parse("personalnoteapp://configure/$appWidgetId")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        /**
         * Spora/programa linkli bir görevin tikine basılınca: görevi widget'tan tamamlamak yerine
         * uygulamayı açıp o görev için set/tekrar/ağırlık giriş ekranını (akordeon) açtırır.
         */
        private fun workoutCompleteIntent(context: Context, taskId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // Her görev için benzersiz data → PendingIntent'ler karışmasın.
                data = Uri.parse("personalnoteapp://complete_workout/$taskId")
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_COMPLETE_WORKOUT)
                putExtra(MainActivity.EXTRA_WIDGET_TASK_ID, taskId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        suspend fun requestUpdate(context: Context) {
            TaskWidget().updateAll(context)
        }

        /**
         * Widget üzerinden bir görevi tamamlar; uygulamadaki davranışı birebir yansıtır. Ağ
         * senkronizasyonunu BURADA yapmaz (arayüzü bekletmemek için) — çağıran önce [requestUpdate]
         * ile anında yeniler, ardından [pushSync] ile gönderir. Geri al için tamamlamadan ÖNCEKİ
         * görevi döndürür (null = görev bulunamadı).
         */
        suspend fun completeTask(context: Context, taskId: String): Task? {
            val ep = entryPoint(context)
            val task = ep.taskRepository().getById(taskId) ?: return null
            val now = System.currentTimeMillis()
            // Tekrarlayan görev ileri sarılır (açık kalır), normal görev kapanır.
            val result = task.withCompletion(now)
            ep.taskRepository().upsert(result)
            ep.notificationService().cancelReminder(task.id)
            if (!result.isDone && ep.appPreferences().systemAlertsEnabled.first()) {
                ep.notificationService().scheduleReminder(result, ep.appPreferences().reminderMinutes.first())
            }
            return task
        }

        /**
         * Widget üzerinden bir alt görevin (checklist maddesi) tamamlanma durumunu değiştirir.
         * Uygulamayı açmadan, gömülü alt görev listesindeki ilgili maddeyi ters çevirir; aynı satıra
         * tekrar dokununca geri alınır. Ana görevi tamamlamaz (uygulama içi davranışla birebir).
         * Ağ senkronizasyonunu burada yapmaz — çağıran önce [requestUpdate] sonra [pushSync] çağırır.
         */
        suspend fun toggleSubtask(context: Context, taskId: String, subtaskId: String) {
            val ep = entryPoint(context)
            val task = ep.taskRepository().getById(taskId) ?: return
            if (task.subtasks.none { it.id == subtaskId }) return
            val updated = task.copy(
                subtasks = task.subtasks.map {
                    if (it.id == subtaskId) it.copy(isDone = !it.isDone) else it
                },
                updatedAt = System.currentTimeMillis(),
            )
            ep.taskRepository().upsert(updated)
        }

        /** Geri al şeridi için tamamlanan görevi serileştirir (tamamlamadan önceki hâli). */
        fun encodeUndo(task: Task): String = json.encodeToString(task.toJson())

        /** Geri al: [encodeUndo] ile saklanan görevi eski hâline (tamamlanmadan önce) geri yükler. */
        suspend fun restoreTask(context: Context, taskJson: String) {
            val ep = entryPoint(context)
            val task = runCatching { json.decodeFromString<TaskJson>(taskJson).toDomain() }.getOrNull() ?: return
            ep.taskRepository().upsert(task.copy(updatedAt = System.currentTimeMillis()))
            if (!task.isDone && ep.appPreferences().systemAlertsEnabled.first()) {
                ep.notificationService().scheduleReminder(task, ep.appPreferences().reminderMinutes.first())
            }
        }

        /**
         * Drive senkronizasyonunu çalıştırır (çevrimdışıyken sessizce yutar). Arayüz güncellendikten
         * SONRA çağrılır; böylece tik atınca widget anında tepki verir, ağ işi arkada sürer.
         */
        suspend fun pushSync(context: Context) {
            runCatching { entryPoint(context).syncRepository().pushToDrive() }
        }
    }
}

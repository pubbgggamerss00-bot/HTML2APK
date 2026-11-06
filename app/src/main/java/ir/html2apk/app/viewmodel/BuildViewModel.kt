package ir.html2apk.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.html2apk.app.buildengine.ApkBuildEngine
import ir.html2apk.app.buildengine.BuildSink
import ir.html2apk.app.buildengine.OutputStore
import ir.html2apk.app.model.BuildRequest
import ir.html2apk.app.model.BuildStage
import ir.html2apk.app.model.FormState
import ir.html2apk.app.model.LogLine
import ir.html2apk.app.model.LogType
import ir.html2apk.app.util.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { IDLE, RUNNING, SUCCESS, FAILED }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val form: FormState = FormState(),
    val currentStage: Int = -1,
    val currentStageStartedAt: Long = 0L,
    val stageDurationsMs: List<Long?> = List(BuildStage.COUNT) { null },
    val logs: List<LogLine> = emptyList(),
    val output: OutputStore.SavedOutput? = null,
    val error: String? = null,
)

class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var buildJob: Job? = null

    // ---------------- form updates ----------------

    fun updateForm(transform: (FormState) -> FormState) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val (name, size) = OutputStore.queryFileInfo(getApplication(), uri)
            val lower = (name ?: "").lowercase()
            updateForm { f ->
                f.copy(
                    inputUri = uri,
                    inputName = name,
                    inputSize = size,
                    appName = f.appName.ifBlank {
                        name?.substringBeforeLast('.')
                            ?.replace(Regex("[^A-Za-z0-9 _-]"), "")
                            ?.trim()
                            ?.ifBlank { null } ?: "MyApp"
                    }
                )
            }
            _state.update { it.copy(error = null) }
        }
    }

    fun onIconPicked(uri: Uri) {
        updateForm { it.copy(iconUri = uri) }
    }

    fun clearIcon() {
        updateForm { it.copy(iconUri = null) }
    }

    // ---------------- build control ----------------

    fun startBuild() {
        // The whole synchronous section is guarded — no input, however malformed,
        // can take the app down before the build coroutine even starts.
        try {
            startBuildInternal()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            recordFatal(t)
        }
    }

    private fun startBuildInternal() {
        val current = _state.value
        if (current.phase == Phase.RUNNING) return

        val form = current.form
        val uri = form.inputUri
            ?: run { _state.update { it.copy(error = "ابتدا فایل HTML یا ZIP را انتخاب کنید") }; return }
        val name = form.inputName ?: "index.html"

        val pkg = Validators.validatePackageName(form.packageName)
        if (!pkg.isValid) {
            _state.update { it.copy(error = pkg.errorMessage) }
            return
        }

        _state.update {
            it.copy(
                phase = Phase.RUNNING,
                logs = emptyList(),
                stageDurationsMs = List(BuildStage.COUNT) { null },
                currentStage = -1,
                currentStageStartedAt = 0L,
                output = null,
                error = null,
                form = it.form.copy(packageName = pkg.normalized),
            )
        }

        val request = BuildRequest(
            inputUri = uri,
            inputName = name,
            isZip = name.lowercase().endsWith(".zip"),
            appName = form.appName.ifBlank { "MyApp" },
            packageName = pkg.normalized,
            versionCode = form.versionCode.trim().toIntOrNull()?.coerceIn(1, 2_100_000_000) ?: 1,
            versionName = form.versionName.ifBlank { "1.0" },
            orientation = form.orientation,
            iconUri = form.iconUri,
            keystorePassword = form.keystorePassword,
            keyAlias = form.keyAlias,
            internetPermission = form.internetPermission,
        )

        val engine = ApkBuildEngine(getApplication())
        val sink = object : BuildSink {
            override fun onStageStart(stage: BuildStage) {
                _state.update {
                    it.copy(
                        currentStage = stage.index,
                        currentStageStartedAt = System.currentTimeMillis(),
                        logs = it.logs + LogLine(
                            System.currentTimeMillis(), LogType.INFO,
                            "── مرحله ${stage.index + 1}/${BuildStage.COUNT}: ${stage.title}"
                        )
                    )
                }
            }

            override fun onStageEnd(stage: BuildStage, durationMs: Long) {
                _state.update {
                    it.copy(
                        stageDurationsMs = it.stageDurationsMs.toMutableList()
                            .also { l -> l[stage.index] = durationMs }
                    )
                }
            }

            override fun log(type: LogType, message: String) {
                _state.update { it.copy(logs = it.logs + LogLine(System.currentTimeMillis(), type, message)) }
            }
        }

        buildJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = engine.build(request, sink)
                _state.update { it.copy(phase = Phase.SUCCESS, output = result, currentStage = -1) }
            } catch (ce: CancellationException) {
                _state.update {
                    it.copy(
                        phase = Phase.IDLE, currentStage = -1,
                        logs = it.logs + LogLine(System.currentTimeMillis(), LogType.ERROR, "ساخت لغو شد")
                    )
                }
            } catch (t: Throwable) {
                // catching Throwable (not just Exception) is deliberate: OutOfMemoryError,
                // NoClassDefFoundError & friends would otherwise kill the whole process —
                // the reported “app crashes when pressing build”. Any failure is now
                // surfaced as a readable terminal error instead.
                recordFatal(t)
            }
        }
    }

    /**
     * Turns ANY build failure — including Error-level ones — into a FAILED state with
     * the exception class + first stack frames printed into the terminal log, so the
     * user always sees what went wrong instead of the app silently dying.
     */
    private fun recordFatal(t: Throwable) {
        val message = t.message?.take(300) ?: t.javaClass.simpleName
        val stamped = System.currentTimeMillis()
        val fatalLines = buildList {
            add(LogLine(stamped, LogType.ERROR, "خطا: $message"))
            add(LogLine(stamped, LogType.ERROR, "نوع استثنا: ${t.javaClass.name}"))
            t.stackTrace.take(8).forEach { frame ->
                add(LogLine(stamped, LogType.ERROR, "    at $frame"))
            }
            t.cause?.let { c ->
                add(
                    LogLine(
                        stamped, LogType.ERROR,
                        "علت: ${c.javaClass.simpleName}: ${c.message?.take(200)}"
                    )
                )
            }
        }
        _state.update {
            it.copy(
                phase = Phase.FAILED,
                currentStage = -1,
                error = "ساخت ناموفق بود: $message",
                logs = it.logs + fatalLines,
            )
        }
    }

    fun cancelBuild() {
        buildJob?.cancel()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun reset() {
        _state.update { UiState(form = it.form.copy(inputUri = null, inputName = null, inputSize = null)) }
    }

    // ---------------- output actions ----------------

    fun installApk() {
        val output = _state.value.output ?: return
        try {
            getApplication<Application>().startActivity(
                OutputStore.installIntent(getApplication(), output)
            )
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    error = "نصب ناموفق بود: ${e.message ?: "نصاب یافت نشد"} — " +
                        "اجازهٔ «نصب برنامه‌های ناشناس» را برای این اپ فعال کنید"
                )
            }
        }
    }

    fun shareApk() {
        val output = _state.value.output ?: return
        try {
            getApplication<Application>().startActivity(
                OutputStore.shareIntent(getApplication(), output)
            )
        } catch (e: Exception) {
            _state.update { it.copy(error = "اشتراک‌گذاری ناموفق بود: ${e.message}") }
        }
    }

    fun openDownloadsFolder() {
        try {
            getApplication<Application>().startActivity(
                OutputStore.openDownloadsFolderIntent()
            )
        } catch (e: Exception) {
            _state.update { it.copy(error = "بازکردن پوشهٔ دانلودها ناموفق بود") }
        }
    }
}

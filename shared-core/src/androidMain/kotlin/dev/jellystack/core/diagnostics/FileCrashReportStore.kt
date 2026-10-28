package dev.jellystack.core.diagnostics

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Keeps the last crash report as one JSON file. Used from the uncaught-exception handler, so it stays dependency free. */
class FileCrashReportStore(
    private val file: File,
) : CrashReportStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun write(report: CrashReport) {
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(json.encodeToString(CrashReport.serializer(), report))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    override fun read(): CrashReport? {
        if (!file.isFile) return null
        return try {
            json.decodeFromString(CrashReport.serializer(), file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override fun clear() {
        file.delete()
    }
}

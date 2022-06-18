import KeySettings.setSolverCommand
import com.github.ajalt.mordant.animation.progressAnimation
import com.vdurmont.semver4j.Semver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

fun downloadFile(url: URL, localArchive: Path) {
    t.info("Download $url to $localArchive")
    val progress = t.progressAnimation {
        text("${localArchive.fileName}")
        percentage()
        progressBar()
        completed()
        speed("B/s")
        timeRemaining()
    }

    val connection = url.openConnection()
    val input = connection.getInputStream()

    val total = connection.getHeaderField("content-length")?.toLong()
    progress.updateTotal(total)

    val readableByteChannel: ReadableByteChannel = Channels.newChannel(input)
    ReadableConsumerByteChannel(readableByteChannel, progress::update).use {
        FileOutputStream(localArchive.toFile()).use { local ->
            local.channel.transferFrom(it, 0, Long.MAX_VALUE)
            total?.let { progress.update(it, it) }
            progress.update()
        }
    }
}

fun getInstallationPath(solver: RemoteSolver, version: RemoteSolverVersion): Path =
    getInstallationPath(solver.name, version.version)

fun getInstallationPath(solver: String, version: String): Path =
    CONFIG.installationPathFile.resolve(solver).resolve(version)


fun updateRemoteRepository() {
    t.info("Update remote repository information: ${CONFIG.repositoryCacheFile}")
    val url = URL(CONFIG.repositoryUrl)
    url.openStream().bufferedReader().use { remote ->
        val content = remote.readText()
        CONFIG.repositoryCacheFile.bufferedWriter().use { local ->
            local.write(content)
        }
    }
}

fun readRemoteRepository(): RemoteRepository {
    if (!CONFIG.repositoryCacheFile.exists()) {
        updateRemoteRepository()
    }
    val text = CONFIG.repositoryCacheFile.readText()
    return Json.decodeFromString(text)
}

fun readLocalRepository(): LocalRepository {
    if (!CONFIG.localInformationFile.exists()) {
        val local = LocalRepository(formatVersion = FORMAT_VERSION)
        CONFIG.localInformationFile.parent.createDirectories()
        CONFIG.localInformationFile.writeText(jsonWrite.encodeToString(local))
        return local
    }
    val text = CONFIG.localInformationFile.readText()
    return Json.decodeFromString(text)
}

fun saveLocalRepository(local: LocalRepository) {
    CONFIG.localInformationFile.writeText(jsonWrite.encodeToString(local))
}

fun checkForUpdates(): Map<String, String> {
    val remote = readRemoteRepository()
    val local = readLocalRepository()
    val latestRemoteVersion: Map<String, String> = remote.findLatestVersion()
    val latestLocalVersion: Map<String, String> = local.findLatestVersion()

    return latestLocalVersion.filter { (k, v) ->
        latestRemoteVersion[k]?.let { r ->
            val a = Semver(v)
            val b = Semver(r)
            a < b
        } ?: false
    }
}


fun enableSolverVersion(solver: String, version: String?) {
    val local = readLocalRepository()
    val v = (version ?: local.findLatestVersion()[solver])
        ?: error("No solver $solver installed.")
    val solverVersion = local.getSolverVersion(solver, v)
        ?: error("Could not find an installed version for $solver.")
    val exec = getExecutionPath(solver, v, solverVersion)
    KeySettings.use {
        it.setSolverCommand(solver, exec.toString())
    }
}

private fun getExecutionPath(
    solver: String,
    v: String,
    solverVersion: InstalledSolverVersion
) = getInstallationPath(solver, v).resolve(solverVersion.executable)

internal fun Path.deleteRecursively() {
    Files.walk(this)
        .sorted(Comparator.reverseOrder())
        .forEach { obj: Path -> obj.deleteIfExists() }
}


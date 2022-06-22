import KeySettings.setSolverCommand
import com.github.ajalt.mordant.animation.progressAnimation
import com.vdurmont.semver4j.Semver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * This method downloads a file from the give URL and saves it into the given target directory. The filename is
 * determined automatically from the URL.
 *
 * Note that the final filename may not be part of the given URL if the connection is redirected.
 *
 * @param url the URL where the file to download is located
 * @param targetDir the local directory where the file will be stored
 * @return the local path of the downloaded file
 */
fun downloadFile(url: URL, targetDir: Path): Path {

    val name = File(url.path).name
    var resultPath: Path = targetDir.resolve(name)

    // for php redirects we have to read the filename that is redirected to
    if (url.path.endsWith(".php")) {
        // separate connection just to determine the filename
        val nameConnection = url.openConnection() as HttpURLConnection
        nameConnection.instanceFollowRedirects = false;
        val loc: String = nameConnection.getHeaderField("Location")
        val location: Path = Paths.get(loc).fileName
        resultPath = targetDir.resolve(location.fileName)
    }

    t.info("Download $url to $resultPath")
    val progress = t.progressAnimation {
        text("${resultPath.fileName}")
        percentage()
        progressBar()
        completed()
        speed("B/s")
        timeRemaining()
    }

    val connection = url.openConnection() as HttpURLConnection
    val input = connection.getInputStream()

    val total = connection.getHeaderField("content-length")?.toLong()
    progress.updateTotal(total)

    val readableByteChannel: ReadableByteChannel = Channels.newChannel(input)
    ReadableConsumerByteChannel(readableByteChannel, progress::update).use {
        FileOutputStream(resultPath.toFile()).use { local ->
            local.channel.transferFrom(it, 0, Long.MAX_VALUE)
            total?.let { progress.update(it, it) }
            progress.update()
        }
    }
    return resultPath
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


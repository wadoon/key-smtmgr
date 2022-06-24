import com.vdurmont.semver4j.Semver
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Current version of the program, needed for repository layout.
 */
const val FORMAT_VERSION = 1

const val VERSION = "1.0-alpha-2"

/**
 * The name of the program, used for configuration path
 */
const val NAME = "key-smtmgr"

/**
 *
 */
val CONFIG_HOME by lazy {
    val xdgconfig = System.getenv("XDG_CONFIG_HOME")
    val home = System.getenv("HOME")
    val path: Path = if (xdgconfig != null) {
        Paths.get(xdgconfig, NAME)
    } else {
        Paths.get(home, ".${NAME}")
    }
    path.createDirectories()
    path
}

val CONFIG_PATH: Path by lazy { CONFIG_HOME.resolve("config.json") }

val jsonWrite = Json {
    encodeDefaults = true
    prettyPrint = true
}

val CONFIG by lazy {
    if (CONFIG_PATH.exists()) {
        val text = CONFIG_PATH.readText()
        Json.decodeFromString(text)
    } else {
        println("Create new configuration file at $CONFIG_PATH")
        val config = Config()
        CONFIG_PATH.writeText(jsonWrite.encodeToString(config))
        config
    }
}

val DATA_HOME: Path by lazy {
    val s = System.getenv("XDG_DATA_HOME")
    if (s == null) {
        val home = System.getenv("HOME")
        // if not set, use default value as defined in standard:
        // https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html#variables
        Paths.get(home, ".local", "share")
    } else {
        Paths.get(s)
    }
}

@Serializable
data class Config(
    val stableRepoUrl: String = "https://raw.githubusercontent.com/wadoon/key-smtmgr/stable/repo.json",
    val nightlyRepoUrl: String = "https://raw.githubusercontent.com/wadoon/key-smtmgr/main/repo.json",
    var nightlyChannel: Boolean = false,
    val installationDirname: String = "key-smtmgr",
    val repositoryCache: String = "repository.cache.json"
) {
    val installationPathFile: Path by lazy { DATA_HOME.resolve(installationDirname) }
    val localInformationFile: Path by lazy { installationPathFile.resolve("info.json") }
    val repositoryCacheFile: Path by lazy { CONFIG_HOME.resolve(repositoryCache) }
}

@Serializable
data class RemoteRepository(
    val updated: String,
    val formatVersion: Int,
    val latestVersion: String,
    val latestDownload: String,
    val solvers: MutableList<RemoteSolver> = arrayListOf()
) {
    fun findLatestVersion(): Map<String, String> {
        return solvers.associate {
            val highest = it.versions.map { v -> v.version }.maxBy { version -> Semver(version) }
            it.name to highest
        }
    }

    fun findSolverVersion(solver: String, version: String): Pair<RemoteSolver, RemoteSolverVersion>? {
        val s = solvers.find { it.name == solver }
        val v = s?.versions?.find { it.version == version }

        if (s == null || v == null) return null
        return s to v
    }
}

@Serializable
data class RemoteSolver(
    val name: String, val license: String, val homepage: String,
    val description: String,
    val versions: MutableList<RemoteSolverVersion> = arrayListOf()
)

@Serializable
data class RemoteSolverVersion(
    val version: String, val description: String = "",
    val releaseDate: String,
    val download: DownloadUrls,
    val executable: String
) {
    val currentDownloadUrl: String
        get() {
            val os = System.getProperty("os.name")
            return if (os.startsWith("Windows"))
                download.win
            else if (os.startsWith("Mac"))
                download.mac
            else
                download.linux
        }
}

@Serializable
data class DownloadUrls(val linux: String, val win: String, val mac: String)


@Serializable
data class LocalRepository(
    val formatVersion: Int,
    val installed: MutableList<LocalSolver> = arrayListOf()
) {
    fun findLatestVersion(): Map<String, String> {
        return installed.associate {
            val highest = it.versions.map { v -> v.version }.maxBy { version -> Semver(version) }
            it.name to highest
        }
    }

    fun isInstalled(name: String, version: String): Boolean {
        return installed.find { it.name == name }?.versions?.find { it.version == version } != null
    }

    fun install(solver: RemoteSolver, solverVersion: RemoteSolverVersion) {
        val lsolver = getSolver(solver.name)
            ?: LocalSolver(
                name = solver.name, license = solver.license, homepage = solver.homepage,
                description = solver.description
            ).also { installed.add(it) }

        lsolver.versions.find { it.version == solverVersion.version }
            ?: InstalledSolverVersion(
                solverVersion.version, solverVersion.description, solverVersion.releaseDate,
                solverVersion.executable
            ).also { lsolver.versions.add(it) }
    }

    fun removeSolverVersion(solver: String, version: String) {
        getSolver(solver)?.let { s ->
            s.versions.removeIf { it.version == version }
            if (s.versions.isEmpty()) {
                installed.remove(s)
            }
        }
    }

    fun getSolver(solver: String) = installed.find { it.name == solver }
    fun getSolverVersion(solver: String, version: String?) =
        getSolver(solver)?.versions?.find { it.version == version }
}

@Serializable
data class LocalSolver(
    val name: String, val license: String, val homepage: String,
    val description: String,
    val versions: MutableList<InstalledSolverVersion> = arrayListOf()
)

@Serializable
data class InstalledSolverVersion(
    val version: String,
    val description: String = "",
    val releaseDate: String,
    val executable: String
)


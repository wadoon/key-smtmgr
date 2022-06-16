import com.vdurmont.semver4j.Semver
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText

/**
 * Current version of the program, needed for repository layout.
 */
const val VERSION = 1

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
    if (xdgconfig != null) {
        Paths.get(xdgconfig, NAME)
    } else {
        Paths.get(home, NAME)
    }
}

val CONFIG_PATH by lazy { CONFIG_HOME.resolve("config.json") }

val CONFIG by lazy {
    val text = CONFIG_PATH.readText()
    Json.decodeFromString<Config>(text)
}


@Serializable
data class Config(
    val repositoryUrl: String = "https://xxx",
    val installationPath: String = "\$XDG_DATA_PATH/key-smtmgr",
    val repositoryCache: String = "repository.cache.json"
) {
    val installationPathFile: Path by lazy { Paths.get(installationPath) }
    val localInformationFile: Path by lazy { installationPathFile.resolve("info.json") }
    val repositoryCacheFile: Path by lazy { CONFIG_HOME.resolve(repositoryCache) }
}

@Serializable
data class RemoteRepository(
    val updated: String,
    val requiredVersion: Int,
    val solvers: MutableList<RemoteSolver> = arrayListOf()
) {
    fun findLatestVersion(): Map<String, String> {
        return solvers.map {
            val highest = it.versions.map { it.version }.maxBy { Semver(it) }
            it.name to highest
        }.toMap()
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
                download.macos
            else
                download.linux
        }
}

@Serializable
data class DownloadUrls(val linux: String, val win: String, val macos: String)


@Serializable
data class LocalRepository(
    val formatVersion: Int,
    val installed: MutableList<LocalSolver> = arrayListOf()
) {
    fun findLatestVersion(): Map<String, String> {
        return installed.map {
            val highest = it.versions.map { it.version }.maxBy { Semver(it) }
            it.name to highest
        }.toMap()
    }
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


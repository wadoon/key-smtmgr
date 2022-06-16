import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.vdurmont.semver4j.Semver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.rauschig.jarchivelib.ArchiverFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.io.path.*


fun updateRemoteRepository() {
    println("Update remote repository information: ${CONFIG.repositoryCacheFile}")
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
        val local = LocalRepository(formatVersion = VERSION)
        CONFIG.localInformationFile.parent.createDirectories()
        CONFIG.localInformationFile.writeText(jsonWrite.encodeToString(local))
        return local
    }
    val text = CONFIG.localInformationFile.readText()
    return Json.decodeFromString(text)
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


class SmtMgr : CliktCommand() {
    val version by option("--version").flag()
    val verbose by option("-v", "--verbose").flag()

    override fun run() {
        if (verbose) {
            println("key-smtmgr -- Alexander Weigl")
            println("version ${VERSION}")
            println("config home $CONFIG_HOME")
            println("config path $CONFIG_PATH")
            println("config: $CONFIG")
        }
    }
}

class UpdateRemoteRepository : CliktCommand(name = "update") {
    override fun run() {
        echo("Updating the remote repository information.")
        updateRemoteRepository()
        echo("Repository information is updated.")
        for ((solver, ver) in checkForUpdates()) {
            echo("$solver is updatable to $ver")
            echo("Use: `key-smtmgr install --enable $solver $ver")
        }
    }
}

class List : CliktCommand(name = "list") {
    override fun run() {
        val remote = readRemoteRepository()
        val local = readLocalRepository()

        for (solver in remote.solvers) {
            println("------------------------------------")
            println("Solver: ${solver.name}")
            println("License: ${solver.license}")
            println("Homepage: ${solver.homepage}")
            println(solver.description)
            println("Versions:")
            for (version in solver.versions) {
                val isInstalled = local.isInstalled(solver.name, version.version)
                println("\t* ${version.version} ${version.releaseDate} ${if (isInstalled) "(INSTALLED)" else ""}")
                println("\t  ${version.description}")
            }
            print("------------------------------------")
        }
    }
}

class InstallSolver : CliktCommand(name = "install") {
    val enable by option("--enable").flag()
    val solver by argument("SOLVER")
    val version by argument("VERSION")

    override fun run() {
        val remote = readRemoteRepository()
        val p = remote.findSolverVersion(solver, version)
        if (p != null) {
            val (solver, version) = p
            installSolver(solver, version)
        } else {
            echo("Solver $solver:$version is unknown. ")
            return
        }
    }

    fun installSolver(solver: RemoteSolver, solverVersion: RemoteSolverVersion) {
        val tempDir = Files.createTempDirectory("download_$NAME")
        val url = URL(solverVersion.currentDownloadUrl)
        val name = File(url.path).name
        val localArchive = tempDir.resolve(name)
        val installationPath = getInstallationPath(solver, solverVersion)

        echo("Installing to $installationPath")

        val readableByteChannel: ReadableByteChannel = Channels.newChannel(url.openStream())
        readableByteChannel.use {
            FileOutputStream(localArchive.toFile()).use { local ->
                local.channel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE)
            }
        }

        ArchiverFactory.createArchiver(localArchive.toFile())
            ?.extract(localArchive.toFile(), installationPath.toFile())
    }
}

fun getInstallationPath(solver: RemoteSolver, version: RemoteSolverVersion): Path =
    getInstallationPath(solver.name, version.version)

fun getInstallationPath(solver: String, version: String) =
    CONFIG.installationPathFile.resolve(solver).resolve(version)


class RemoveSolver : CliktCommand(name = "remove") {
    override fun run() {
        TODO("Not yet implemented")
    }
}

class EnableSolver : CliktCommand(name = "enable") {
    override fun run() {
        TODO("Not yet implemented")
    }
}


fun main(args: Array<String>) {
    SmtMgr()
        .subcommands(
            UpdateRemoteRepository(), InstallSolver(),
            RemoveSolver(), EnableSolver(), List()
        )
        .main(args)
}


fun expandVariables(text: String): String {
    val pattern = "\\$(\\{(\\w+)\\}|[a-zA-Z\\d]+)"
    val expr: Pattern = Pattern.compile(pattern)
    val matcher: Matcher = expr.matcher(text)
    val envMap = System.getenv()
    var text: String = text
    while (matcher.find()) {
        var envValue: String = envMap.get(matcher.group(1).toUpperCase()) ?: ""
        envValue = envValue.replace("\\", "\\\\") ?: ""
        val subexpr: Pattern = Pattern.compile(Pattern.quote(matcher.group(0)))
        text = subexpr.matcher(text).replaceAll(envValue)
    }
    return text
}
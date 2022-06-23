import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.vdurmont.semver4j.Semver
import org.rauschig.jarchivelib.ArchiverFactory
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.system.exitProcess

internal val t = Terminal()

class SmtMgr : CliktCommand() {
    private val version by option("--version").flag()
    private val verbose by option("-v", "--verbose").flag()

    override fun run() {
        if (version) {
            t.println("version $FORMAT_VERSION")
            exitProcess(0)
        }

        if (verbose) {
            t.println("$NAME -- Alexander Weigl <weigl@kit.edu>")
            t.println("Version:           $FORMAT_VERSION")
            t.println("CONFIG_HOME:       $CONFIG_HOME")
            t.println("CONFIG_PATH:       $CONFIG_PATH")
            t.println("KEY_SETTINGS_PATH: $KEY_SETTINGS_PATH")
            t.println("CONFIG:            $CONFIG")
        }
    }
}

class UpdateRemoteRepository : CliktCommand(name = "update") {
    override fun run() {
        t.println("Updating the remote repository information.")
        updateRemoteRepository()
        t.println("Repository information is updated.")
        for ((solver, ver) in checkForUpdates()) {
            t.println("$solver is updatable to $ver")
            t.println("Use: `$NAME install --enable $solver $ver")
        }

        val remote = readRemoteRepository()
        val curVersion = Semver(VERSION, Semver.SemverType.LOOSE)
        val latestVersion = Semver(remote.latestVersion, Semver.SemverType.LOOSE)

        if (latestVersion > curVersion) {
            t.warning("A new version ${remote.latestVersion} (current: $curVersion) is available for download.")
            t.warning("URL: ${remote.latestDownload}")
        }

        if (remote.formatVersion > FORMAT_VERSION) {
            t.danger("FORMAT CHANGE CONSIDER UPDATE")
        }

    }
}

class List : CliktCommand(name = "list") {
    override fun run() {
        val remote = readRemoteRepository()
        val local = readLocalRepository()
        for (solver in remote.solvers) {
            t.println("------------------------------------")
            t.println("${bold("Solver:")} ${solver.name}")
            t.println(
                "${bold("License:")} ${solver.license}", whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD, width = 60
            )
            t.println("Homepage: ${solver.homepage}")
            t.println(
                solver.description,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD, width = 60
            )
            t.println("Versions:")
            for (version in solver.versions) {
                val isInstalled = local.isInstalled(solver.name, version.version)
                t.println("\t* ${version.version} ${version.releaseDate} ${if (isInstalled) "(INSTALLED)" else ""}")
                t.println("\t  ${version.description}")
            }
        }
    }
}

class InstallSolver : CliktCommand(name = "install") {
    private val enable by option("--enable").flag()
    private val solver by argument("SOLVER")
    private val version by argument("VERSION")

    override fun run() {
        val remote = readRemoteRepository()
        val p = remote.findSolverVersion(solver, version)
        if (p != null) {
            val (solver, version) = p
            installSolver(solver, version)
            if (enable) {
                enableSolverVersion(solver.name, version.version)
            }
        } else {
            t.danger("Solver $solver:$version is unknown.")
            return
        }
    }

    private fun installSolver(solver: RemoteSolver, solverVersion: RemoteSolverVersion) {
        val tempDir = Files.createTempDirectory("download_$NAME")
        val url = URL(solverVersion.currentDownloadUrl)
        val installationPath = getInstallationPath(solver, solverVersion)

        if (installationPath.exists()) {
            t.warning("$installationPath already exists. Abort installation.")
            t.warning("Use `$NAME remove $solver $version` to clear previous installation")
            return
        }

        t.info("Installing to $installationPath")

        // may change the localArchive filename if the connection is redirected
        val localPath = downloadFile(url, tempDir)
        val name = localPath.fileName.toString()

        try {
            t.info("Unpacking archive to $installationPath")
            ArchiverFactory.createArchiver(localPath.toFile())
                ?.extract(localPath.toFile(), installationPath.toFile())
        } catch (ex : Exception) {
            when(ex) {
                is java.lang.IllegalArgumentException, is java.io.IOException -> {
                    val target = installationPath.resolve(name)
                    t.info("Downloaded is an executable. Just copy it to $target")
                    installationPath.createDirectories()
                    Files.copy(localPath, target)
                }
                else -> throw ex
            }
        }

        t.info("Register solver locally.")
        val local = readLocalRepository()
        local.install(solver, solverVersion)
        saveLocalRepository(local)

        t.info("Solver can be called: $local")
    }
}


class RemoveSolver : CliktCommand(name = "remove") {
    private val solver by argument("SOLVER")
    private val version by argument("VERSION")
    override fun run() {
        val local = readLocalRepository()
        val path = getInstallationPath(solver, version)
        path.deleteRecursively()
        local.removeSolverVersion(solver, version)
        saveLocalRepository(local)
        enableSolverVersion(solver, null)
    }
}

class EnableSolver : CliktCommand(name = "enable") {
    private val solver by argument("SOLVER")
    private val version by argument("VERSION").optional()
    override fun run() {
        enableSolverVersion(solver, version)
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

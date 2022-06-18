import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter

/**
 *
 * @author Alexander Weigl
 * @version 1 (17.06.22)
 */

val KEY_SETTINGS_PATH: Path =
    Paths.get(System.getProperty("user.home"), ".key", "proofIndependentSettings.props")

object KeySettings {
    fun load(): Properties {
        val properties = Properties()
        KEY_SETTINGS_PATH.bufferedReader().use { properties.load(it) }
        return properties
    }

    fun save(properties: Properties): Properties {
        KEY_SETTINGS_PATH.bufferedWriter().use { properties.store(it, "") }
        return properties
    }

    fun use(fn: (Properties) -> Unit) = save(load().also { fn(it) })


    fun Properties.setSolverCommand(solver: String, path: String) {
        val key = "[SMTSettings]solverCommand$solver"
        this[key] = path
    }
}
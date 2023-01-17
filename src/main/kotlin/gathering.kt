import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlin.collections.List

/**
 *
 * @author Alexander Weigl
 * @version 1 (17.01.23)
 */

fun main(args: Array<String>) = Gathering().main(args)

class Gathering() : CliktCommand() {
    val json = Json { prettyPrint = true }

    val z3 by option("--z3").flag()
    val cvc5 by option("--cvc5").flag()
    val mathsat by option("--mathsat").flag()
    val eldarica by option("--eldarica").flag()
    val smtinterpol by option("--smtinterpol").flag()
    val all by option("--all").flag()

    val file = File("repo.json")
    val repo by lazy {
        val text = file.readText()
        Json.decodeFromString<RemoteRepository>(text)
    }

    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()


    override fun run() {
        runBlocking {
            launch { if (z3 || all) z3() }
            launch { if (cvc5 || all) cvc5() }
            launch { if (mathsat || all) mathsat() }
            launch { if (eldarica || all) eldarica() }
            launch { if (smtinterpol || all) smtinterpol() }
        }

        file.bufferedWriter().use {
            val value = json.encodeToString(repo)
            it.write(value)
        }
    }

    private suspend fun smtSolverFromReleases(
        solverName: String, repo: String,
        linuxName: String, winName: String, macosName: String,
        executable: String
    ) {
        val remote = findByName(solverName)
        remote.versions.clear()
        val githubReleases = getGithubReleases(repo)
        for (release in githubReleases) {
            remote.versions.add(
                RemoteSolverVersion(
                    release.tag_name, release.body ?: "",
                    release.published_at,
                    DownloadUrls(
                        linux = release.assets.findUrl(linuxName),
                        win = release.assets.findUrl(winName),
                        mac = release.assets.findUrl(macosName),
                    ),
                    executable
                )
            )
        }
    }

    private suspend fun cvc5() = smtSolverFromReleases(
        "cvc5", "cvc5/cvc5", "-Linux", "-Win64.exe",
        "-macOS", "./bin/z3"
    )

    private suspend fun smtinterpol() = Unit

    private suspend fun eldarica() = smtSolverFromReleases(
        "eldarica", "uuverifiers/eldarica",
        "-bin-", "-bin-", "-bin-", "eldarica"
    )

    private suspend fun z3() = smtSolverFromReleases(
        "z3", "Z3Prover/z3", "x64-glibc", "x64-win", "x64-osx", "./bin/z3"
    )

    private fun mathsat() {
        val mathsatRemote = findByName("MathSAT")
        val url = "https://mathsat.fbk.eu/downloadall.html"
        val request = HttpRequest.newBuilder(URI(url)).build()
        val response = client.send(request) { HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8) }
        require(response.statusCode() == 200) { "Receive status code ${response.statusCode()} from $url" }
        val body = response.body()
        val version = "Version (\\d+\\.\\d+\\.\\d+) \\((\\w{3} \\d{1,2}, \\d{4})\\)".toRegex()
        mathsatRemote.versions.clear()
        version.findAll(body).forEach {
            val version = it.groupValues[1]
            val date = it.groupValues[2]
            mathsatRemote.versions.add(
                RemoteSolverVersion(
                    version, "", date,
                    DownloadUrls(
                        linux = body.findMathsatUrl(version, "x64-glibc"),
                        win = body.findMathsatUrl(version, "win"),
                        mac = body.findMathsatUrl(version, "osx"),
                    ),
                    "./bin/z3"
                )
            )
        }
    }


    private fun findByName(s: String) = repo.solvers.find { it.name == s } ?: error("Could not find $s in repo.json")

    private suspend fun getGithubReleases(repo: String): List<Release> {
        val format = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }


        val url = "https://api.github.com/repos/$repo/releases"
        val request = HttpRequest.newBuilder(URI(url)).build()
        val response = client.send(request) { HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8) }
        require(response.statusCode() == 200) { "Receive status code ${response.statusCode()} from $url" }
        return format.decodeFromString<ArrayList<Release>>(response.body())
    }
}

private fun String.findMathsatUrl(version: String, os: String): String? {
    val re = "download.php?file=.*$version.*$os\\.(zip|tar\\.gz)".toRegex()
    return re.find(this)?.let { it.value }
}

private fun List<Asset>.findUrl(contains: String) =
    find { it.browser_download_url.contains(contains) }?.browser_download_url

@Serializable
data class Release(
    val url: String,
    val html_url: String,
    val assets_url: String,
    val upload_url: String,
    val tarball_url: String?,
    val zipball_url: String?,
    val id: Int,
    val node_id: String,
    val tag_name: String,
    /**Specifies the commitish value that determines where the Git tag is created from.  */
    val target_commitish: String,
    val name: String?,
    val body: String?,
    /** true to create a draft (unpublished) release, false to create a published one. */
    val draft: Boolean,
    /** Whether to identify the release as a prerelease or a full release. */
    val prerelease: Boolean,
    val created_at: String,
    val published_at: String,
    //val author: Author,
    val assets: ArrayList<Asset>
)


@Serializable
data class Asset(
    val url: String,
    val browser_download_url: String,
    val id: String,
    val node_id: String,
    val name: String,
    val label: String?,
    val state: String,
    val content_type: String,
    val size: Int,
    val download_count: Int,
    val created_at: String,
    val updated_at: String,
)
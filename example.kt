// 镜像 example.py 的 Kotlin 示例
// 运行需在 classpath 中包含 LanzouCloudApi.kt、kotlinx-coroutines-core、timber
import com.theveloper.pixelplay.data.github.LanzouCloudApi
import kotlinx.coroutines.runBlocking

fun main() {
    val softwareTag = "arm64"
    val versionTag = "1.3.2-32-20260731"
    val password = "dtu2"
    val targetName = "PixelPlay-$versionTag-release.apk"
    val shareUrl = "https://wwbvc.lanzouv.com/b011m9azlg"

    val api = LanzouCloudApi()
    val result = runBlocking {
        api.getUrl(shareUrl, password, targetName)
    }

    result.fold(
        onSuccess = { url ->
            println(url)
        },
        onFailure = { error ->
            System.err.println("Failed: ${error.message}")
        }
    )
}

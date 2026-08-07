// 模仿 example.py 的 Kotlin 示例
// 运行需在 classpath 中包含 LanzouCloudApi.kt、kotlinx-coroutines-core、timber
import com.theveloper.pixelplay.data.github.LanzouCloudApi
import kotlinx.coroutines.runBlocking

fun main() {
    val softwareTag = "arm64"
    val versionTag = "1.3.3-33-20260801"
    val password = "dtu2"
    val targetName = "PixelPlay-$versionTag-release.apk"
    val shareUrl = "https://wwbvc.lanzouv.com/b011m9azlg"

    val api = LanzouCloudApi()
    val result = runBlocking {
        api.resolveShare(shareUrl, password)
    }

    result.fold(
        onSuccess = { files ->
            val target = files.firstOrNull { it.fileName == targetName }
            if (target != null) {
                println(target.downloadUrl)
            } else {
                System.err.println("Not Found: '$targetName' in file list")
                files.forEach { println("  - ${it.fileName}  (${it.fileSize})") }
            }
        },
        onFailure = { error ->
            System.err.println("Failed: ${error.message}")
        }
    )
}

import java.nio.file.Paths

object Versions {
    const val minSdk = 24
    const val targetSdk = 34

    const val appcompatVersion = "1.6.1"
    const val coreKtVersion = "1.12.0"
    const val coroutineVersion = "1.7.2"
    const val composeUiVersion = "1.5.4"
    const val composeCompilerVersion = "1.5.0"
    const val composeActivityVersion = "1.8.0"
    const val composeMaterialVersion = "1.2.0-alpha10"
    const val ktorVersion = "2.3.4"
    const val kotlinVersion = "1.9.0"
    const val kotlinxSerializationVersion = "1.5.1"
    const val lifecycleRuntimeVersion = "2.6.2"
    const val navVersion = "2.7.4"
    const val jnanoidVersion = "2.0.0"
    const val materialVersion = "1.10.0"
    const val workRuntimeVersion = "2.8.1"
    const val yukiVersion = "1.2.0"
    const val stubVersion = "1.0-SNAPSHOT"

    /**
     * Generate an increasing version code
     * so that you don't have to worry about
     */
    fun next(id: String = "app"): Int {
        val file = Paths.get("build", "version_$id").toFile()
        val base = if (file.exists()) {
            file.readText().toIntOrNull() ?: 0
        } else {
            file.parentFile.mkdirs()
            file.createNewFile()
            0
        }
        file.writeText((base + 1).toString())
        return base + 1
    }
}
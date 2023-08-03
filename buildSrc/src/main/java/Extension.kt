import org.gradle.api.Project
import java.util.Properties

fun Project.properties(name: String): Properties {
    val result = Properties()
    file(name).reader().use {
        result.load(it)
    }
    return result
}

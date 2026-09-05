package wrasse.apply

import com.varlanv.wrasse.lang.FileApplyResult
import com.varlanv.wrasse.lang.WPatchApplier
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

@TaskAction
fun wrasseApply(@Input(inferTaskDependency = false) patchDir: Path) {
    val result = WPatchApplier.apply(patchDir)
    if (result.files.isEmpty()) println("wrasse: nothing to apply under $patchDir")
    for (file in result.files) {
        when (file) {
            is FileApplyResult.Applied -> println("wrasse: fixed ${file.filePath} (${file.editCount} edits)")
            is FileApplyResult.Skipped -> println("wrasse: skipped ${file.filePath} (${file.reason})")
            is FileApplyResult.Failed -> error("wrasse: failed ${file.filePath}: ${file.reason}")
        }
    }
}

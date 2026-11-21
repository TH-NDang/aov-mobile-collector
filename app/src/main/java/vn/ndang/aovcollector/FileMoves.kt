package vn.ndang.aovcollector

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Puts a finished `.part` in place of [target] in one step, replacing what is there; false when it could not, as
 * [File.renameTo]. On the phone renameTo already replaced (a Linux rename, which this still is), but on Windows it
 * refuses a target that exists: every journal saved a second time failed there, in the unit tests only. A link at
 * [target] is replaced itself, never followed.
 */
fun File.moveOnto(target: File): Boolean = try {
    Files.move(toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    true
} catch (_: Exception) {
    false
}

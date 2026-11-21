package vn.ndang.aovcollector

import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import org.junit.Assume.assumeTrue

/**
 * A symbolic link at [link] to [target], for the tests that a link never leads a run's files elsewhere. Windows makes
 * one only with Developer Mode on or as administrator: there the test is skipped rather than failed, since nothing of
 * the app was tried. Anywhere else a link that cannot be made is a failure.
 */
fun symlink(link: File, target: File) {
    try {
        Files.createSymbolicLink(link.toPath(), target.toPath())
    } catch (e: FileSystemException) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) throw e
        assumeTrue("Windows không cho tạo symlink (bật Developer Mode để chạy): ${e.reason}", false)
    }
}

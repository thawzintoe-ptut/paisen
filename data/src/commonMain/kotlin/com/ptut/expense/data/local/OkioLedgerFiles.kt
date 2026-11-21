package com.ptut.expense.data.local

import okio.FileSystem
import okio.Path.Companion.toPath

/** Takes the file system rather than naming `FileSystem.SYSTEM`, which common code cannot see. */
internal class OkioLedgerFiles(private val fileSystem: FileSystem) : LedgerFiles {
    override fun exists(path: String): Boolean = fileSystem.exists(path.toPath())
    override fun copy(from: String, to: String) = fileSystem.copy(from.toPath(), to.toPath())
    override fun move(from: String, to: String) = fileSystem.atomicMove(from.toPath(), to.toPath())
    override fun read(path: String): ByteArray = fileSystem.read(path.toPath()) { readByteArray() }
}

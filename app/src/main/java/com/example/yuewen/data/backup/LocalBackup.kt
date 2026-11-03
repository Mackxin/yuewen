package com.example.yuewen.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 本地文件夹那条通道（v2.9）。
 *
 * 走 SAF（`content://`），不是直接写文件路径 —— 理由不是「技术上更先进」，
 * 而是从 Android 10 起分区存储把「随便往哪儿写文件」这条路堵死了：
 * 想拿到一个可写的目录，唯一的正路就是让用户自己在系统选择器里点一个授权。
 *
 * 所以这里的关系是「用户在的时候授权一次，之后一直用」：
 * 授权的 tree uri 存进设置，配合 `takePersistableUriPermission` 长期有效。
 *
 * ## ⚠️ 这个 uri **不能进备份**
 *
 * `content://com.android.externalstorage.documents/tree/…` 里的
 * tree id 是**本机存储卷的标识**，换一台设备、甚至同一台设备换张 SD 卡，
 * 那个 id 就对不上了。恢复到新机器上会得到一个「授权已失效」的目录，
 * 用户点备份直接失败，而且不知道该怎么修。
 * 所以它归在「本机设置」，不进 [com.example.yuewen.data.SettingsRepository.BACKUP_KEYS]。
 */
object LocalBackup {

    /**
     * 这个授权目录下的所有文件名。
     *
     * 用的是 `buildChildDocumentsUriUsingTree`（带 tree 的组合 uri）而不是
     * `buildChildDocumentsUri`：后者在只有 tree 授权、没逐个文件授权的情况下
     * 会被 `SecurityException` 拦下。
     */
    fun listNames(resolver: ContentResolver, treeUri: Uri): List<String> =
        listEntries(resolver, treeUri).map { it.first }

    /** 写一份备份；同名文件直接**覆盖**。 */
    fun write(resolver: ContentResolver, treeUri: Uri, name: String, content: String) {
        val dir = childrenUri(treeUri)
        val existing = listEntries(resolver, treeUri).firstOrNull { it.first == name }?.second
        val target = existing
            ?: DocumentsContract.createDocument(resolver, dir, "application/json", name)
            ?: throw BackupException("系统没有返回文件句柄，可能这个文件夹不允许写入")

        // ⚠️ 模式必须带 `t`（truncate）。只要 `w` 的话，
        // 覆盖一个更长的旧文件会留下尾巴，得到一个**坏掉的 JSON**——
        // 而且恢复的时候才发现，那时旧的备份已经被覆盖了。
        val out = try {
            resolver.openOutputStream(target, "wt")
        } catch (e: Exception) {
            throw BackupException("打不开这个文件：" + (e.message ?: "没有写入权限"))
        } ?: throw BackupException("打不开这个文件，可能这个文件夹不允许写入")

        out.use {
            it.write(content.toByteArray(Charsets.UTF_8))
            it.flush()
        }
    }

    /** 读一个备份文件的内容。 */
    fun read(resolver: ContentResolver, treeUri: Uri, name: String): String {
        val uri = listEntries(resolver, treeUri).firstOrNull { it.first == name }?.second
            ?: throw BackupException("这个文件夹里已经没有「$name」了")
        val input = try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            throw BackupException("读不了这个文件：" + (e.message ?: "没有读取权限"))
        } ?: throw BackupException("读不了这个文件，可能授权已经失效")

        return input.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /**
     * 删掉指定的几个文件。
     *
     * 找不到的跳过：调用方是按「目录里有哪些文件」算出来要删谁，
     * 中间被用户手动删掉了不该算失败。
     */
    fun delete(resolver: ContentResolver, treeUri: Uri, names: List<String>) {
        if (names.isEmpty()) return
        val wanted = names.toHashSet()
        listEntries(resolver, treeUri)
            .filter { it.first in wanted }
            .forEach { (_, uri) ->
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
    }

    /** 目录下的 (文件名, 文档 uri) 列表。 */
    private fun listEntries(resolver: ContentResolver, treeUri: Uri): List<Pair<String, Uri>> {
        val out = ArrayList<Pair<String, Uri>>()
        val cursor = try {
            resolver.query(
                childrenUri(treeUri),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )
        } catch (e: Exception) {
            // 授权被撤销（用户在原目录上点了「移除授权」，或者换机后 uri 失效）
            // 时这里是第一个会炸的地方，翻译成人话再抛。
            throw BackupException("访问不了这个文件夹，授权可能已经失效，请重新选一次")
        }
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out.add(name to DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            }
        }
        return out
    }

    private fun childrenUri(treeUri: Uri): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
}

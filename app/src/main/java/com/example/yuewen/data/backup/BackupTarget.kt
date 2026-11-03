package com.example.yuewen.data.backup

/**
 * 备份的三条通道（v2.9）。
 *
 * 枚举而不是字符串常量：这三条通道在界面上、在 `when` 里、在提示文案里都要被列举，
 * 用字符串的话迟早出现 `"gist"` / `"Gist"` 两种写法各写一半的错。
 *
 * 标签（[label]）直接写在枚举上，而不是留在界面层：
 * 保存失败时的提示是「已备份到 GitHub Gist」这种句子，
 * 由 ViewModel 拼出来（它才知道哪一步失败了）。标签放在界面层的话，
 * ViewModel 就只能拿到一个 enum 再想办法翻译，多绕一层。
 */
enum class BackupTarget(val label: String) {
    /** GitHub Gist：一条 PAT 就能用，不用自己搭服务器。 */
    GIST("GitHub Gist"),

    /** WebDAV：坚果云 / Nextcloud / 自建 NAS 都算这一类。 */
    WEBDAV("WebDAV 网盘"),

    /** 本地文件夹：走 SAF 授权，可以选 SD 卡或同步盘挂载的目录。 */
    LOCAL("本地文件夹")
}

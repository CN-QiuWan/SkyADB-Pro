package com.qwadb.app.ui.files

/**
 * 打开「文件管理」时的初始侧栏意图。
 *
 * 快捷操作入口（上传到设备 / 从设备拉取）需要先设定默认激活哪一侧，
 * 再跳转到文件管理页。使用一次性意图而非导航参数，避免改动既有路由结构。
 */
object FilesLaunchIntent {
    private var pendingPane: FilePaneId? = null

    /** 设定下一次进入文件管理页时默认激活的侧栏。 */
    fun request(pane: FilePaneId) {
        pendingPane = pane
    }

    /** 取出并清空待生效的侧栏；未设置时返回 null。 */
    fun consume(): FilePaneId? {
        val pane = pendingPane
        pendingPane = null
        return pane
    }
}

package com.chuhe.quickjump

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 快捷跳转 —— 直达快递取件码/待取页面。
 * 不联网、不申请权限、不收集不存储任何数据。
 * 自定义入口链接仅保存在本机 App 私有目录（SharedPreferences），不上传。
 *
 * 跳转模型（入口核对参考开源项目 EidenLiu/pickup-code-launcher，MIT）：
 * 每个目标由多条「跳转链」组成（目标包名 + 该包名下多条 deeplink），按顺序尝试；
 * 全部失败后依次拉起候选包名的 App 首页；最后尝试网页兜底。
 *
 * 入口链接可自定义：长按按钮编辑，保存在本地后优先于内置默认链接生效。
 * 目标 App 更新导致链接失效时，无需重新打包，长按按钮改链接即可。
 */
class MainActivity : AppCompatActivity() {

    /** 一条跳转链：目标包名 + 该包名下按优先级排列的多条 deeplink */
    private data class Chain(val pkg: String, val uris: List<String>)

    /**
     * @param chains      跳转链列表，按顺序尝试（如：正版抖音链 → 极速版链）
     * @param openAppPkgs 链全部失败后，依次尝试直接拉起的 App 包名
     * @param webUri      网页兜底入口（先试目标 App 内打开，再试浏览器）
     */
    private data class Entry(
        val title: String,
        val chains: List<Chain>,
        val openAppPkgs: List<String> = emptyList(),
        val webUri: String? = null
    )

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 首页 2×3 按钮：点击跳转，长按编辑入口链接
        bind(R.id.btn_cainiao, KEY_CAINIAO)
        bind(R.id.btn_taobao, KEY_TAOBAO)
        bind(R.id.btn_pdd, KEY_PDD)
        bind(R.id.btn_express, KEY_EXPRESS)
        bind(R.id.btn_pdd_pending, KEY_PDD_PENDING)
        bind(R.id.btn_douyin, KEY_DOUYIN)

        // 处理桌面长按快捷方式带入的跳转请求
        handleDispatchIntent(intent)

        // 左下角问号：点开「关于」，长按编辑内容
        findViewById<View>(R.id.btn_about).setOnClickListener { showAbout() }
        findViewById<View>(R.id.btn_about).setOnLongClickListener {
            showEditAbout()
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDispatchIntent(intent)
    }

    private fun bind(viewId: Int, key: String) {
        val btn = findViewById<View>(viewId)
        btn.setOnClickListener { jumpFor(key) }
        btn.setOnLongClickListener {
            showEditDialog(key)
            true
        }
    }

    private fun handleDispatchIntent(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_TARGET) ?: return
        if (ENTRIES.containsKey(key)) jumpFor(key)
    }

    /** 应用用户自定义链接（若有）后执行跳转 */
    private fun jumpFor(key: String) {
        val base = ENTRIES.getValue(key)
        val custom = loadCustomUris(key)
        val entry = if (custom.isNotEmpty()) {
            // 自定义链接要覆盖每一条链：抖音有正版/极速版两条链，
            // 只改第一条会让极速版用户的自定义链接完全不生效。
            base.copy(chains = base.chains.map { it.copy(uris = (custom + it.uris).distinct()) })
        } else {
            base
        }
        jump(entry)
    }

    /** 多级回退跳转：链 → 拉起 App → 网页 */
    private fun jump(entry: Entry) {
        // 第 1 级：逐条链、逐条 deeplink 尝试（setPackage 限定目标 App）
        for (chain in entry.chains) {
            for (uri in chain.uris) {
                if (tryOpen(uri, chain.pkg)) return
            }
        }
        // 第 2 级：直接拉起目标 App（支持多个候选包名，如正版/极速版）
        for (pkg in entry.openAppPkgs) {
            if (tryOpenApp(entry.title, pkg)) return
        }
        // 第 3 级：网页兜底——先试目标 App 内渲染，再退浏览器
        entry.webUri?.let { web ->
            for (chain in entry.chains) {
                if (tryOpen(web, chain.pkg)) return
            }
            if (tryOpen(web, null)) return
        }
        showDialog("暂时无法打开「${entry.title}」\n可长按按钮更新入口链接，或确认对应 App 已安装")
    }

    private fun tryOpen(uri: String, pkg: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (pkg != null) intent.setPackage(pkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun tryOpenApp(title: String, pkg: String): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                or Intent.FLAG_ACTIVITY_NEW_TASK
        )
        return try {
            startActivity(launch)
            Toast.makeText(
                this,
                "${title}没有公开直达链接，已打开官方 App，请在 App 内查看",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("无法打开")
            .setMessage(msg)
            .setPositiveButton("知道了", null)
            .show()
    }

    // ---------- 自定义入口链接（仅存本机） ----------

    private fun loadAboutText(): String =
        prefs.getString(PREF_ABOUT, null) ?: getString(R.string.about_text)

    /** 「关于」弹窗：展示说明文字，URL 自动可点击；附入口体检入口 */
    private fun showAbout() {
        val tv = TextView(this).apply {
            text = loadAboutText()
            textSize = 14f
            setLineSpacing(4f, 1f)
            setPadding(48, 32, 48, 16)
            movementMethod = LinkMovementMethod.getInstance()
            Linkify.addLinks(this, Linkify.WEB_URLS)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .setNeutralButton("入口体检") { _, _ -> showDiagnostics() }
            .show()
    }

    /**
     * 入口体检：逐条检测每条 deeplink 在当前手机上是否有页面响应。
     * 结果可选中复制或截图发给作者，用于远程定位失效链接。
     */
    private fun showDiagnostics() {
        val sb = StringBuilder()
        sb.appendLine("设备: Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})")
        sb.appendLine("App 版本: ${BuildConfig.VERSION_NAME}")
        sb.appendLine()
        for ((_, entry) in ENTRIES) {
            sb.appendLine("【${entry.title}】")
            for (chain in entry.chains) {
                val installed = isAppInstalled(chain.pkg)
                sb.appendLine(" 目标App ${if (installed) "✓已安装" else "✗未安装"} (${chain.pkg})")
                for (uri in chain.uris) {
                    val ok = installed && canResolve(uri, chain.pkg)
                    sb.appendLine("  ${if (ok) "✓可用" else "✗无响应"} ${shortUri(uri)}")
                }
            }
            sb.appendLine()
        }
        sb.appendLine("✗无响应 = 该链接在当前版本上没有页面处理（未安装或链接已失效）")
        sb.append("长按对应按钮可更新链接；截图发给作者可远程修复")
        val tv = TextView(this).apply {
            text = sb.toString()
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(40, 24, 40, 8)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this)
            .setTitle("入口体检")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun shortUri(uri: String): String =
        if (uri.length <= 60) uri else uri.substring(0, 60) + "..."

    private fun isAppInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun canResolve(uri: String, pkg: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (pkg != null) intent.setPackage(pkg)
        packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** 长按问号：编辑「关于」内容，保存在本机 */
    private fun showEditAbout() {
        val input = EditText(this).apply {
            setText(loadAboutText())
            setSingleLine(false)
            minLines = 8
            gravity = Gravity.TOP
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("编辑关于内容")
            .setMessage("这里的内容会显示在左下角问号弹窗里，链接会自动变成可点击。")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                prefs.edit().putString(PREF_ABOUT, input.text.toString()).apply()
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("恢复默认") { _, _ ->
                prefs.edit().remove(PREF_ABOUT).apply()
                Toast.makeText(this, "已恢复默认内容", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadCustomUris(key: String): List<String> =
        prefs.getString(key, null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /** 长按按钮：编辑该入口的 deeplink 列表，每行一条，按顺序尝试 */
    private fun showEditDialog(key: String) {
        val base = ENTRIES.getValue(key)
        val current = loadCustomUris(key).ifEmpty { base.chains.first().uris }
        val input = EditText(this).apply {
            setText(current.joinToString("\n"))
            setSingleLine(false)
            minLines = 4
            gravity = Gravity.TOP
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("编辑「${base.title}」入口")
            .setMessage(
                "每行一条 deeplink，按顺序尝试。\n" +
                    "目标 App 更新后链接失效时，用「开发者助手」App 查看新链接后填到这里即可。\n" +
                    "选择「恢复默认」将清除自定义内容。"
            )
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val lines = input.text.toString()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                prefs.edit().putString(key, lines.joinToString("\n")).apply()
                Toast.makeText(
                    this,
                    if (lines.isEmpty()) "已清空，将使用内置默认链接" else "已保存 ${lines.size} 条自定义链接",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton("恢复默认") { _, _ ->
                prefs.edit().remove(key).apply()
                Toast.makeText(this, "已恢复内置默认链接", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        private const val PREFS_NAME = "entry_config"
        private const val PREF_ABOUT = "about_text"
        private const val EXTRA_TARGET = "jump_target"

        private const val KEY_CAINIAO = "cainiao"
        private const val KEY_TAOBAO = "taobao"
        private const val KEY_PDD = "pdd"
        private const val KEY_EXPRESS = "express"
        private const val KEY_PDD_PENDING = "pdd_pending"
        private const val KEY_DOUYIN = "douyin"

        // 常用包名
        private const val PKG_DOUYIN = "com.ss.android.ugc.aweme"          // 抖音
        private const val PKG_DOUYIN_LITE = "com.ss.android.ugc.aweme.lite" // 抖音极速版

        // ⚠️ 内置默认入口链接，核对日期：2026-09~10。失效时可长按按钮临时改，或更新此处在打包。
        private val ENTRIES = mapOf(

            // 1) 菜鸟身份码：菜鸟裹裹驿站身份码页
            KEY_CAINIAO to Entry(
                title = "菜鸟身份码",
                chains = listOf(
                    Chain(
                        pkg = "com.cainiao.wireless",
                        uris = listOf("cainiao://desktop/station_code")
                    )
                ),
                openAppPkgs = listOf("com.cainiao.wireless"),
                webUri = "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO"
            ),

            // 2) 淘宝身份码：tbopen 壳链接打开淘宝内 H5 身份码页
            KEY_TAOBAO to Entry(
                title = "淘宝身份码",
                chains = listOf(
                    Chain(
                        pkg = "com.taobao.taobao",
                        uris = listOf(
                            "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1011717%2Flast-mile-fe%2Fend-collect-platform%2Fidentity-code%3Fx-ssr%3Dtrue",
                            "tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1011717%2Flast-mile-fe%2Fend-collect-platform%2Fidentity-code%3Fx-ssr%3Dtrue"
                        )
                    )
                ),
                webUri = "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true"
            ),

            // 3) 淘宝待取快递：淘宝内菜鸟包裹待取列表页（校园版页面）
            KEY_EXPRESS to Entry(
                title = "淘宝待取快递",
                chains = listOf(
                    Chain(
                        pkg = "com.taobao.taobao",
                        uris = listOf(
                            "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1100333%2Flast-mile-fe%2Fm-end-school-tab%2Fhome",
                            "tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1100333%2Flast-mile-fe%2Fm-end-school-tab%2Fhome"
                        )
                    )
                ),
                webUri = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
            ),

            // 4) 拼多多身份码：多多驿站取件码页（tab=ID_CODE），多条降级参数
            KEY_PDD to Entry(
                title = "拼多多身份码",
                chains = listOf(
                    Chain(
                        pkg = "com.xunmeng.pinduoduo",
                        uris = listOf(
                            "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11&refer_page_name=login&refer_page_sn=10169",
                            "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11",
                            "pinduoduo://com.xunmeng.pinduoduo/mdkd/package"
                        )
                    )
                ),
                webUri = "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0"
            ),

            // 5) 拼多多待取快递：多多驿站包裹列表页（默认 tab）
            KEY_PDD_PENDING to Entry(
                title = "拼多多待取快递",
                chains = listOf(
                    Chain(
                        pkg = "com.xunmeng.pinduoduo",
                        uris = listOf("pinduoduo://com.xunmeng.pinduoduo/mdkd/package")
                    )
                ),
                openAppPkgs = listOf("com.xunmeng.pinduoduo"),
                webUri = "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0"
            ),

            // 6) 抖音待取快递：商城订单列表（可看待收货/物流）→ 钱包（我的快递入口）
            //    兼容正版抖音（snssdk1128）与抖音极速版（snssdk2329）
            KEY_DOUYIN to Entry(
                title = "抖音待取快递",
                chains = listOf(
                    Chain(
                        pkg = PKG_DOUYIN,
                        uris = listOf(
                            "snssdk1128://ecom_order_list",
                            "snssdk1128://wallet"
                        )
                    ),
                    Chain(
                        pkg = PKG_DOUYIN_LITE,
                        uris = listOf(
                            "snssdk2329://ecom_order_list",
                            "snssdk2329://wallet"
                        )
                    )
                ),
                openAppPkgs = listOf(PKG_DOUYIN, PKG_DOUYIN_LITE)
            )
        )
    }
}

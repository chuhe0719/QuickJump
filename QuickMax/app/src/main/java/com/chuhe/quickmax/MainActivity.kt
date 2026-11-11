package com.chuhe.quickmax
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.File
import kotlin.math.abs
class MainActivity : AppCompatActivity() {
    private data class Chain(val pkg: String, val uris: List<String>)
    private data class Entry(
        val title: String,
        val chains: List<Chain>,
        val openAppPkgs: List<String> = emptyList(),
        val webUri: String? = null
    )
    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val avatarFile by lazy { File(filesDir, "about_avatar.png") }
    private val homeBgFile by lazy { File(filesDir, "home_bg.jpg") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bind(R.id.btn_cainiao, KEY_CAINIAO)
        bind(R.id.btn_taobao, KEY_TAOBAO)
        bind(R.id.btn_pdd, KEY_PDD)
        bind(R.id.btn_express, KEY_EXPRESS)
        bind(R.id.btn_pdd_pending, KEY_PDD_PENDING)
        bind(R.id.btn_douyin, KEY_DOUYIN)
        bind(R.id.btn_jd, KEY_JD)
        if (savedInstanceState == null) handleDispatchIntent(intent)
        findViewById<View>(R.id.btn_about).setOnClickListener { showAbout() }
        findViewById<View>(R.id.btn_about).setOnLongClickListener {
            showAvatarMenu()
            true
        }
        findViewById<View>(R.id.btn_codes).setOnClickListener { openCodes() }
        applyAvatar()
        applyHomeBackground()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
        if (key == KEY_CODES) {
            intent.removeExtra(EXTRA_TARGET)
            openCodes()
            return
        }
        if (!ENTRIES.containsKey(key)) return
        intent.removeExtra(EXTRA_TARGET)
        jumpFor(key)
    }
    private fun openCodes() {
        startActivity(Intent(this, CodesActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    private var swipeX = 0f
    private var swipeY = 0f
    private var swipeTime = 0L
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeX = event.x
                swipeY = event.y
                swipeTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - swipeX
                val dy = event.y - swipeY
                if (dx < -120 && abs(dx) > abs(dy) * 1.5f &&
                    System.currentTimeMillis() - swipeTime < 900
                ) {
                    openCodes()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
    private fun jumpFor(key: String) {
        val base = ENTRIES.getValue(key)
        val custom = loadCustomUris(key)
        val entry = if (custom.isNotEmpty()) {
            base.copy(chains = base.chains.map { it.copy(uris = (custom + it.uris).distinct()) })
        } else {
            base
        }
        jump(entry, prefs.getBoolean(PREF_CLEAR_TASK_PREFIX + key, false))
    }
    private fun jump(entry: Entry, clearTask: Boolean) {
        for (chain in entry.chains) {
            for (uri in chain.uris) {
                if (tryOpen(uri, chain.pkg, clearTask)) return
            }
        }
        for (pkg in entry.openAppPkgs) {
            if (tryOpenApp(entry.title, pkg, clearTask)) return
        }
        entry.webUri?.let { web ->
            for (chain in entry.chains) {
                if (tryOpen(web, chain.pkg, clearTask)) return
            }
            if (tryOpen(web, null)) return
        }
        showDialog("暂时无法打开「${entry.title}」\n可长按按钮更新入口链接，或确认对应 App 已安装")
    }
    private fun tryOpen(uri: String, pkg: String?, clearTask: Boolean = false): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (pkg != null) intent.setPackage(pkg)
        if (clearTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
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
    private fun tryOpenApp(title: String, pkg: String, clearTask: Boolean = false): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                or Intent.FLAG_ACTIVITY_NEW_TASK
        )
        if (clearTask) launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
    private fun loadAboutText(): String =
        prefs.getString(PREF_ABOUT, null) ?: getString(R.string.about_text)
    private fun showAbout() {
        val tv = TextView(this).apply {
            text = loadAboutText() + "\n\n" + getString(R.string.edit_hint)
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
    private fun showAvatarMenu() {
        AlertDialog.Builder(this)
            .setTitle("外观与关于")
            .setItems(
                arrayOf("从相册换头像", "从相册换主页背景", "编辑关于内容", "恢复默认外观")
            ) { _, which ->
                when (which) {
                    0 -> pickAvatar()
                    1 -> pickHomeBackground()
                    2 -> showEditAbout()
                    3 -> {
                        avatarFile.delete()
                        homeBgFile.delete()
                        applyAvatar()
                        applyHomeBackground()
                        Toast.makeText(this, "已恢复默认外观", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
    private fun pickAvatar() {
        val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(pick, "选择头像图片"), REQ_AVATAR)
        } catch (e: Exception) {
            Toast.makeText(this, "打不开相册", Toast.LENGTH_SHORT).show()
        }
    }
    private fun pickHomeBackground() {
        val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(pick, "选择主页背景图"), REQ_HOME_BG)
        } catch (e: Exception) {
            Toast.makeText(this, "打不开相册", Toast.LENGTH_SHORT).show()
        }
    }
    @Deprecated("Uses the platform activity result API for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_AVATAR -> {
                val bitmap = decodeSquareAvatar(uri)
                if (bitmap == null) {
                    Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    avatarFile.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    applyAvatar()
                    Toast.makeText(this, "已更换头像", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_HOME_BG -> {
                val bitmap = decodeCoverBitmap(uri)
                if (bitmap == null) {
                    Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    homeBgFile.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
                    }
                    applyHomeBackground()
                    Toast.makeText(this, "已更换主页背景", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun decodeSquareAvatar(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1024) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val stream = contentResolver.openInputStream(uri) ?: return null
            val src = stream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(
                src, (src.width - side) / 2, (src.height - side) / 2, side, side
            )
            val scaled = if (side == 256) square else Bitmap.createScaledBitmap(square, 256, 256, true)
            if (scaled !== square) square.recycle()
            if (square !== src) src.recycle()
            scaled
        } catch (e: Exception) {
            null
        }
    }
    private fun decodeCoverBitmap(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val stream = contentResolver.openInputStream(uri) ?: return null
            val src = stream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val metrics = resources.displayMetrics
            val targetRatio = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
            val sourceRatio = src.width.toFloat() / src.height.toFloat()
            val cropped = if (sourceRatio > targetRatio) {
                val width = (src.height * targetRatio).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(src, (src.width - width) / 2, 0, width, src.height)
            } else {
                val height = (src.width / targetRatio).toInt().coerceAtMost(src.height).coerceAtLeast(1)
                Bitmap.createBitmap(src, 0, (src.height - height) / 2, src.width, height)
            }
            val scale = minOf(1f, 1280f / maxOf(cropped.width, cropped.height))
            val result = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    cropped,
                    (cropped.width * scale).toInt(),
                    (cropped.height * scale).toInt(),
                    true
                )
            } else {
                cropped
            }
            if (result !== cropped) cropped.recycle()
            if (cropped !== src) src.recycle()
            result
        } catch (e: Exception) {
            null
        }
    }
    private fun applyAvatar() {
        val button = findViewById<View>(R.id.btn_about)
        val bitmap = if (avatarFile.exists()) {
            try {
                BitmapFactory.decodeFile(avatarFile.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        if (bitmap == null) {
            button.background = ContextCompat.getDrawable(this, R.drawable.bg_about_face)
            return
        }
        val rounded = RoundedBitmapDrawableFactory.create(resources, bitmap).apply {
            isCircular = true
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        button.background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(0x33, 0xFF, 0xFF, 0xFF)),
            rounded,
            mask
        )
    }
    private fun applyHomeBackground() {
        val root = findViewById<View>(R.id.root_view)
        val bitmap = if (homeBgFile.exists()) {
            try {
                BitmapFactory.decodeFile(homeBgFile.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        if (bitmap == null) {
            root.background = ContextCompat.getDrawable(this, R.drawable.bg_screen)
            return
        }
        val layers = arrayOf<android.graphics.drawable.Drawable>(
            android.graphics.drawable.BitmapDrawable(resources, bitmap),
            android.graphics.drawable.ColorDrawable(Color.argb(0x66, 0xFF, 0xFF, 0xFF))
        )
        root.background = android.graphics.drawable.LayerDrawable(layers)
    }
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
    private fun showEditDialog(key: String) {
        val base = ENTRIES.getValue(key)
        val current = loadCustomUris(key).ifEmpty { base.chains.first().uris }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val input = EditText(this).apply {
            setText(current.joinToString("\n"))
            setSingleLine(false)
            minLines = 4
            gravity = Gravity.TOP
            setSelection(text.length)
        }
        container.addView(input)
        val clearTaskCheck = CheckBox(this).apply {
            text = "强制新开目标页（清除该 App 已有页面）\n修复淘宝在后台时跳不动的机型，无需勾选请保持关闭"
            textSize = 13f
            isChecked = prefs.getBoolean(PREF_CLEAR_TASK_PREFIX + key, false)
        }
        container.addView(clearTaskCheck)
        AlertDialog.Builder(this)
            .setTitle("编辑「${base.title}」入口")
            .setMessage(
                "每行一条 deeplink，按顺序尝试。\n" +
                    "目标 App 更新后链接失效时，用「开发者助手」App 查看新链接后填到这里即可。\n" +
                    "选择「恢复默认」将清除全部自定义内容。"
            )
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val lines = input.text.toString()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                prefs.edit()
                    .putString(key, lines.joinToString("\n"))
                    .putBoolean(PREF_CLEAR_TASK_PREFIX + key, clearTaskCheck.isChecked)
                    .apply()
                Toast.makeText(
                    this,
                    if (lines.isEmpty()) "已清空，将使用内置默认链接" else "已保存 ${lines.size} 条自定义链接",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton("恢复默认") { _, _ ->
                prefs.edit().remove(key).remove(PREF_CLEAR_TASK_PREFIX + key).apply()
                Toast.makeText(this, "已恢复内置默认链接", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    companion object {
        private const val PREFS_NAME = "entry_config"
        private const val PREF_ABOUT = "about_text"
        private const val PREF_CLEAR_TASK_PREFIX = "clear_task_"
        private const val EXTRA_TARGET = "jump_target"
        private const val KEY_CAINIAO = "cainiao"
        private const val KEY_TAOBAO = "taobao"
        private const val KEY_PDD = "pdd"
        private const val KEY_EXPRESS = "express"
        private const val KEY_PDD_PENDING = "pdd_pending"
        private const val KEY_DOUYIN = "douyin"
        private const val KEY_JD = "jd"
        private const val KEY_CODES = "codes"
        private const val REQ_AVATAR = 2001
        private const val REQ_HOME_BG = 2002
        private const val PKG_DOUYIN = "com.ss.android.ugc.aweme"
        private const val PKG_DOUYIN_LITE = "com.ss.android.ugc.aweme.lite"
        private val ENTRIES = mapOf(
            KEY_CAINIAO to Entry(
                title = "菜鸟身份码",
                chains = listOf(
                    Chain(
                        pkg = "com.cainiao.wireless",
                        uris = listOf(
                            "cainiao://desktop/station_code",
                            "cainiao://client.main"
                        )
                    )
                ),
                openAppPkgs = listOf("com.cainiao.wireless"),
                webUri = "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO"
            ),
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
            ),
            KEY_JD to Entry(
                title = "京东待取快递",
                chains = listOf(
                    Chain(
                        pkg = "com.jingdong.app.mall",
                        uris = listOf(
                            "openjd://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22orderlist%22%7D"
                        )
                    )
                ),
                openAppPkgs = listOf("com.jingdong.app.mall"),
                webUri = "https://order.jd.com/center/list.action"
            )
        )
    }
}

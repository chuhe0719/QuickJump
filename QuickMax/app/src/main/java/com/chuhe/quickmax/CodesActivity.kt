package com.chuhe.quickmax

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class CodesActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View
    private lateinit var tvPending: TextView
    private lateinit var tvTaken: TextView
    private var items: MutableList<CodeItem> = mutableListOf()
    private var recognizer: TextRecognizer? = null
    private var tagDialog: AlertDialog? = null
    private val cameraPhoto by lazy { File(cacheDir, "camera_photo.jpg") }
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_codes)
        listContainer = findViewById(R.id.list_container)
        emptyView = findViewById(R.id.empty_view)
        tvPending = findViewById(R.id.tv_pending)
        tvTaken = findViewById(R.id.tv_taken)
        findViewById<View>(R.id.btn_back).setOnClickListener { goBack() }
        findViewById<View>(R.id.btn_camera).setOnClickListener { takePhoto() }
        findViewById<View>(R.id.btn_scan).setOnClickListener { openImagePicker() }
        findViewById<View>(R.id.btn_add).setOnClickListener { showAddDialog() }
        findViewById<View>(R.id.btn_clear).setOnClickListener { clearTaken() }
        render()
        if (savedInstanceState == null) handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        recognizer?.close()
        recognizer = null
        super.onDestroy()
    }

    private fun goBack() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    @Deprecated("Uses the platform activity result API for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_PICK -> {
                val uri = data?.data ?: return
                val bitmap = decodeBitmap(uri)
                if (bitmap == null) {
                    toast("图片读取失败，换一张试试")
                    return
                }
                runOcr(bitmap, "截图")
            }
            REQ_CAMERA -> {
                val bitmap = decodeFileBitmap(cameraPhoto)
                if (bitmap == null) {
                    toast("照片读取失败，可改用截图识别")
                    return
                }
                runOcr(bitmap, "拍照")
            }
        }
    }

    private fun handleShareIntent(source: Intent?) {
        val incoming = source ?: return
        if (incoming.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val stream = incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        if (stream != null) {
            incoming.removeExtra(Intent.EXTRA_STREAM)
            val bitmap = decodeBitmap(stream)
            if (bitmap == null) toast("图片读取失败") else runOcr(bitmap, "分享")
            return
        }
        val text = incoming.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            incoming.removeExtra(Intent.EXTRA_TEXT)
            val codes = CodeParser.parse(text)
            if (codes.isEmpty()) toast("分享内容里没找到取件码")
            else showCandidates(codes, "分享", text)
        }
    }

    private fun openImagePicker() {
        val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(pick, "选择含有取件码的截图"), REQ_PICK)
        } catch (e: Exception) {
            toast("打不开相册")
        }
    }

    private fun takePhoto() {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cameraPhoto)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val targets = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (targets.isEmpty()) {
                toast("没找到相机 App，可用「从截图识别」")
                return
            }
            for (info in targets) {
                grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            toast("打不开相机：${e.message ?: "请用截图识别"}")
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 1800) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (e: Exception) {
        null
    }

    private fun decodeFileBitmap(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1800) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            rotateIfNeeded(file, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateIfNeeded(file: File, bitmap: Bitmap): Bitmap = try {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }
    } catch (e: Exception) {
        bitmap
    }

    private fun getRecognizer(): TextRecognizer {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
        return recognizer!!
    }

    private fun runOcr(bitmap: Bitmap, source: String) {
        val bar = ProgressBar(this).apply {
            isIndeterminate = true
            setPadding(0, 36, 0, 36)
        }
        val progress = AlertDialog.Builder(this)
            .setTitle("正在识别")
            .setMessage("多角度识别中（含横拍、手写增强），图片不会上传")
            .setView(bar)
            .setCancelable(false)
            .create()
        progress.show()
        val variants = mutableListOf<Pair<String, InputImage>>()
        try {
            variants.add("原图" to InputImage.fromBitmap(bitmap, 0))
            enlargeForOcr(bitmap)?.let { variants.add("放大" to InputImage.fromBitmap(it, 0)) }
            variants.add("旋转90°" to InputImage.fromBitmap(bitmap, 90))
            variants.add("旋转270°" to InputImage.fromBitmap(bitmap, 270))
            enhanceForHandwriting(bitmap)?.let { variants.add("手写增强" to InputImage.fromBitmap(it, 0)) }
        } catch (e: Exception) {
            progress.dismiss()
            toast("图片处理失败：${e.message ?: "可手动添加"}")
            return
        }
        recognizeNext(variants, 0, LinkedHashSet(), StringBuilder(), progress, source, null)
    }

    private fun recognizeNext(
        variants: List<Pair<String, InputImage>>,
        index: Int,
        found: MutableSet<String>,
        raw: StringBuilder,
        progress: AlertDialog,
        source: String,
        firstError: String?
    ) {
        if (isFinishing || isDestroyed) return
        if (index >= variants.size) {
            progress.dismiss()
            when {
                found.isEmpty() && firstError != null -> toast("识别失败：$firstError，可手动添加")
                found.isEmpty() -> showNoCode(raw.toString())
                else -> showCandidates(found.toList(), source, raw.toString())
            }
            return
        }
        val (label, image) = variants[index]
        try {
            getRecognizer().process(image)
                .addOnSuccessListener { result ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    found.addAll(CodeParser.parse(result.text))
                    raw.append("—— ").append(label).append(" ——\n").append(result.text).append('\n')
                    recognizeNext(variants, index + 1, found, raw, progress, source, firstError)
                }
                .addOnFailureListener { error ->
                    if (isFinishing || isDestroyed) return@addOnFailureListener
                    recognizeNext(
                        variants, index + 1, found, raw, progress, source,
                        firstError ?: (error.message ?: "未知错误")
                    )
                }
        } catch (e: Exception) {
            recognizeNext(
                variants, index + 1, found, raw, progress, source,
                firstError ?: (e.message ?: "识别器不可用")
            )
        }
    }

    private fun enlargeForOcr(bitmap: Bitmap): Bitmap? = try {
        val longest = maxOf(bitmap.width, bitmap.height)
        val ratio = minOf(1.5f, 2600f / longest)
        if (longest >= 2400 || ratio <= 1.05f) {
            null
        } else {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun enhanceForHandwriting(src: Bitmap): Bitmap? = try {
        val matrix = ColorMatrix(
            floatArrayOf(
                1.7f, 0f, 0f, 0f, -70f,
                0f, 1.7f, 0f, 0f, -70f,
                0f, 0f, 1.7f, 0f, -70f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        out
    } catch (e: Exception) {
        null
    }

    private fun showNoCode(raw: String) {
        val preview = raw.take(220).ifBlank { "（没有识别到文字）" }
        AlertDialog.Builder(this)
            .setTitle("没识别到取件码")
            .setMessage("没找到形如 8-2-1234 / A-5-3464 / 29 3 4304 的取件码。\n\n识别到的文字：\n$preview")
            .setPositiveButton("手动添加") { _, _ -> showAddDialog() }
            .setNeutralButton("识别全文") { _, _ -> showRawText(raw) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showCandidates(codes: List<String>, source: String, rawText: String? = null) {
        val checked = BooleanArray(codes.size) { true }
        val guessed = rawText?.let { PageSourceDetector.detect(it) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        root.addView(TextView(this).apply {
            text = "勾选要添加的，重复会自动跳过"
            textSize = 12f
            setTextColor(Color.parseColor("#8A9099"))
        })
        if (guessed != null) {
            root.addView(TextView(this).apply {
                text = "这张图可能是「$guessed」的待取快递"
                textSize = 13f
                setTextColor(Color.parseColor("#1677FF"))
                setPadding(0, dp(10), 0, dp(2))
            })
        }
        val markCheck = CheckBox(this).apply {
            text = if (guessed == null) "" else "把本次添加的码标记为「$guessed」"
            isChecked = false
            textSize = 13f
            setTextColor(Color.parseColor("#30363D"))
            visibility = if (guessed == null) View.GONE else View.VISIBLE
        }
        root.addView(markCheck)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((index, code) in codes.withIndex()) {
            list.addView(CheckBox(this).apply {
                text = code
                isChecked = true
                textSize = 16f
                setTextColor(Color.parseColor("#1F2329"))
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnCheckedChangeListener { _, isChecked -> checked[index] = isChecked }
            })
        }
        val scroller = ScrollView(this).apply {
            addView(list)
            isVerticalScrollBarEnabled = false
        }
        root.addView(
            scroller,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240)
            ).apply { topMargin = dp(8) }
        )
        val builder = AlertDialog.Builder(this)
            .setTitle("找到 ${codes.size} 个取件码")
            .setView(root)
            .setPositiveButton("添加") { _, _ ->
                val picked = codes.filterIndexed { index, _ -> checked[index] }
                addCodes(picked, source, if (markCheck.isChecked) guessed else null)
            }
            .setNegativeButton("取消", null)
        if (rawText != null) {
            builder.setNeutralButton("识别原文") { _, _ ->
                showRawText(rawText) { showCandidates(codes, source, rawText) }
            }
        }
        builder.show()
    }

    private fun showRawText(raw: String, onBack: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle("识别原文")
            .setMessage(raw.ifBlank { "（没有识别到文字）" }.take(3000))
            .setPositiveButton(if (onBack == null) "关闭" else "返回候选") { _, _ ->
                onBack?.invoke()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "例如 8-2-1234 或 29 3 4304"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(false)
            minLines = 2
            gravity = Gravity.TOP
            background = ContextCompat.getDrawable(this@CodesActivity, R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(Color.parseColor("#1F2329"))
            setHintTextColor(Color.parseColor("#A6AFBB"))
            textSize = 15f
        }
        val holder = LinearLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("手动添加取件码")
            .setMessage("空格和 - 都能当分隔符，例如 8-2-1234 / 29 3 4304；多个码换行分隔")
            .setView(holder)
            .setPositiveButton("添加") { _, _ ->
                val codes = CodeParser.splitManual(input.text.toString())
                if (codes.isEmpty()) toast("没读到有效取件码") else addCodes(codes, "手动")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addCodes(codes: List<String>, source: String, platform: String? = null) {
        if (codes.isEmpty()) {
            toast("没有勾选任何取件码")
            return
        }
        val (added, duplicated) = CodeStore.addAll(this, codes, source, platform ?: "")
        render()
        toast(
            if (added > 0) "已添加 $added 个" + if (duplicated > 0) "，跳过 $duplicated 个重复" else ""
            else "都已存在，没有新增"
        )
    }

    private fun render() {
        items = CodeStore.load(this)
        listContainer.removeAllViews()
        val pending = items.count { !it.taken }
        val taken = items.size - pending
        tvPending.text = "未取 $pending"
        tvTaken.text = "已取 $taken"
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val ordered = items.sortedWith(compareBy<CodeItem> { it.taken }.thenByDescending { it.time })
        for (item in ordered) {
            val row = layoutInflater.inflate(R.layout.item_code, listContainer, false)
            val codeView = row.findViewById<TextView>(R.id.tv_code)
            val status = row.findViewById<TextView>(R.id.tv_status)
            val accent = row.findViewById<View>(R.id.view_accent)
            val tagRow = row.findViewById<LinearLayout>(R.id.tag_row)
            codeView.text = item.code
            if (item.taken) {
                row.background = ContextCompat.getDrawable(this, R.drawable.bg_code_card_taken)
                accent.background = ContextCompat.getDrawable(this, R.drawable.bg_accent_taken)
                codeView.paintFlags = codeView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                codeView.setTextColor(Color.parseColor("#9AA1AB"))
                status.text = "已取"
                status.setTextColor(Color.parseColor("#2E7D55"))
                status.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_taken)
            } else {
                row.background = ContextCompat.getDrawable(this, R.drawable.bg_code_card)
                accent.background = ContextCompat.getDrawable(this, R.drawable.bg_accent_pending)
                codeView.setTextColor(Color.parseColor("#1F2329"))
                status.text = "未取"
                status.setTextColor(Color.parseColor("#B54708"))
                status.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_pending)
            }
            row.findViewById<TextView>(R.id.tv_meta).text = "${item.source} · ${formatTime(item.time)}"
            val tags = listOf(item.platform, item.station, item.codeType, item.note)
                .filter { it.isNotBlank() }
            tagRow.removeAllViews()
            if (tags.isEmpty()) {
                tagRow.visibility = View.GONE
            } else {
                tagRow.visibility = View.VISIBLE
                for (tag in tags.take(3)) tagRow.addView(makeTagChip(tag))
                if (tags.size > 3) tagRow.addView(makeTagChip("+${tags.size - 3}", neutral = true))
            }
            row.setOnClickListener { showBigCode(item.code) }
            row.setOnLongClickListener {
                toggleTaken(item.id)
                true
            }
            row.findViewById<View>(R.id.btn_note).setOnClickListener { showTagDialog(item) }
            row.findViewById<View>(R.id.btn_del).setOnClickListener { confirmDelete(item) }
            listContainer.addView(row)
        }
    }

    private fun makeTagChip(text: String, neutral: Boolean = false): TextView {
        val (fill, textColor) = if (neutral) {
            Color.parseColor("#EDF1F6") to Color.parseColor("#7A828E")
        } else {
            tagColors(text)
        }
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(textColor)
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(fill)
            }
            setPadding(dp(8), dp(3), dp(8), dp(3))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(96)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }
    }

    private data class TagDraft(
        var platform: String,
        var station: String,
        var codeType: String,
        var note: String
    )

    private fun showTagDialog(
        item: CodeItem,
        draft: TagDraft = TagDraft(item.platform, item.station, item.codeType, item.note)
    ) {
        val noteInput = EditText(this).apply {
            setText(draft.note)
            hint = "补充备注，例如：三栋楼下"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            background = ContextCompat.getDrawable(this@CodesActivity, R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(Color.parseColor("#1F2329"))
            setHintTextColor(Color.parseColor("#A6AFBB"))
            textSize = 15f
            setSelection(text.length)
        }
        val refresh = {
            draft.note = noteInput.text.toString()
            tagDialog?.dismiss()
            showTagDialog(item, draft)
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        holder.addView(
            buildTagRow("平台类型", TAG_PLATFORM, DEFAULT_PLATFORMS, draft.platform, refresh) {
                draft.platform = it
            }
        )
        holder.addView(
            buildTagRow("驿站类型", TAG_STATION, DEFAULT_STATIONS, draft.station, refresh) {
                draft.station = it
            }
        )
        holder.addView(
            buildTagRow("身份码类型", TAG_CODE_TYPE, DEFAULT_CODE_TYPES, draft.codeType, refresh) {
                draft.codeType = it
            }
        )
        holder.addView(
            TextView(this).apply {
                text = "补充备注"
                textSize = 12f
                setTextColor(Color.parseColor("#8A9099"))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )
        holder.addView(
            noteInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
        val scroller = ScrollView(this).apply {
            addView(holder)
            isVerticalScrollBarEnabled = false
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("标签设置")
            .setView(scroller)
            .setPositiveButton("保存") { _, _ ->
                updateTags(item.id, draft.platform, draft.station, draft.codeType, noteInput.text.toString().trim())
            }
            .setNeutralButton("清空标签") { _, _ -> updateTags(item.id, "", "", "", "") }
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnDismissListener { tagDialog = null }
        tagDialog = dialog
        dialog.show()
    }

    private fun buildTagRow(
        label: String,
        key: String,
        defaults: List<String>,
        selected: String,
        onOptionsChanged: () -> Unit,
        onSelect: (String) -> Unit
    ): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#8A9099"))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(TextView(this).apply {
            text = "＋ 添加 · 长按删除"
            textSize = 10f
            setTextColor(Color.parseColor("#A6AFBB"))
        })
        container.addView(header)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<TextView>()
        var current = selected
        val options = CodeStore.loadTagOptions(this, key, defaults)
        for (option in options) {
            val chip = TextView(this).apply {
                text = option
                textSize = 12f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                maxLines = 1
                setOnClickListener {
                    current = if (current == option) "" else option
                    chips.forEach { paintChip(it, it.text.toString() == current) }
                    onSelect(current)
                }
                setOnLongClickListener {
                    confirmRemoveTag(key, defaults, option, onOptionsChanged)
                    true
                }
            }
            row.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            )
            chips.add(chip)
        }
        chips.forEach { paintChip(it, it.text.toString() == current) }
        row.addView(TextView(this).apply {
            text = "＋"
            textSize = 14f
            setTextColor(Color.parseColor("#1677FF"))
            background = ContextCompat.getDrawable(this@CodesActivity, R.drawable.bg_chip_blue)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { promptAddTag(key, defaults, onOptionsChanged) }
        })
        val scroller = HorizontalScrollView(this).apply {
            addView(row)
            isHorizontalScrollBarEnabled = false
        }
        container.addView(
            scroller,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
        return container
    }

    private fun promptAddTag(key: String, defaults: List<String>, onAdded: () -> Unit) {
        val input = EditText(this).apply {
            hint = "例如：保育驿站"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            background = ContextCompat.getDrawable(this@CodesActivity, R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(Color.parseColor("#1F2329"))
            setHintTextColor(Color.parseColor("#A6AFBB"))
            textSize = 15f
        }
        val holder = LinearLayout(this).apply {
            setPadding(dp(24), dp(12), dp(24), dp(4))
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("添加自定义选项")
            .setView(holder)
            .setPositiveButton("添加") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    toast("没填内容")
                    return@setPositiveButton
                }
                CodeStore.addTagOption(this, key, defaults, value)
                toast("已添加「$value」")
                onAdded()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmRemoveTag(key: String, defaults: List<String>, option: String, onRemoved: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("删除选项")
            .setMessage("从这一层移除「$option」？不影响已保存的取件码。")
            .setPositiveButton("删除") { _, _ ->
                CodeStore.removeTagOption(this, key, defaults, option)
                onRemoved()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun paintChip(chip: TextView, active: Boolean) {
        chip.background = ContextCompat.getDrawable(
            this,
            if (active) R.drawable.bg_chip_active else R.drawable.bg_chip_blue
        )
        chip.setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#1677FF"))
    }

    private fun updateTags(id: Long, platform: String, station: String, codeType: String, note: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items[index] = items[index].copy(
            platform = platform,
            station = station,
            codeType = codeType,
            note = note
        )
        CodeStore.save(this, items)
        render()
    }

    private fun tagColors(tag: String): Pair<Int, Int> = when {
        tag.contains("菜鸟") -> Color.parseColor("#E4EEFF") to Color.parseColor("#1677FF")
        tag.contains("丰巢") || tag.contains("妈妈驿站") || tag.contains("兔喜") ->
            Color.parseColor("#E6F4EC") to Color.parseColor("#2E7D55")
        tag.contains("淘宝") -> Color.parseColor("#FFE7D2") to Color.parseColor("#B54708")
        tag.contains("拼多多") || tag.contains("京东") ->
            Color.parseColor("#FDEAE8") to Color.parseColor("#B42318")
        tag.contains("抖音") -> Color.parseColor("#EDF0F5") to Color.parseColor("#3A4150")
        else -> Color.parseColor("#E9EFF8") to Color.parseColor("#5B6472")
    }

    private fun showBigCode(code: String) {
        val view = layoutInflater.inflate(R.layout.dialog_big_code, null)
        view.findViewById<TextView>(R.id.tv_big_code).text = code
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun toggleTaken(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = items[index]
        items[index] = current.copy(taken = !current.taken)
        CodeStore.save(this, items)
        render()
        toast(if (current.taken) "已恢复为未取" else "「${current.code}」已标记为已取")
    }

    private fun confirmDelete(item: CodeItem) {
        AlertDialog.Builder(this)
            .setTitle("删除取件码")
            .setMessage("确定删除「${item.code}」吗？")
            .setPositiveButton("删除") { _, _ ->
                items.removeAll { it.id == item.id }
                CodeStore.save(this, items)
                render()
                toast("已删除")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearTaken() {
        if (items.none { it.taken }) {
            toast("还没有已取的取件码")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("清空已取")
            .setMessage("会删除所有标记为已取的取件码，未取的保留。")
            .setPositiveButton("清空") { _, _ ->
                items = items.filter { !it.taken }.toMutableList()
                CodeStore.save(this, items)
                render()
                toast("已清空")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatTime(time: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (dx > 120 && abs(dx) > abs(dy) * 1.5f &&
                    System.currentTimeMillis() - downTime < 900
                ) {
                    goBack()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private companion object {
        const val REQ_PICK = 1001
        const val REQ_CAMERA = 1002
        const val TAG_PLATFORM = "platform"
        const val TAG_STATION = "station"
        const val TAG_CODE_TYPE = "code_type_v2"
        val DEFAULT_PLATFORMS = listOf("菜鸟", "淘宝", "拼多多", "京东", "抖音", "快手", "微信")
        val DEFAULT_STATIONS =
            listOf("菜鸟驿站", "妈妈驿站", "丰巢", "兔喜", "快递柜", "自提点", "代收点", "保育驿站")
        val DEFAULT_CODE_TYPES = listOf("拼多多身份码", "淘宝身份码", "菜鸟身份码")
    }
}

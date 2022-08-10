package net.codebot.pdfviewer

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import java.io.*
import java.util.*


class MainActivity : AppCompatActivity() {
    private inner class StandardButton : AppCompatRadioButton {
        constructor(context: Context?) : super(context) {}
        constructor(context: Context?, s: String?) : super(context) {
            width = 200
            height = 100
            text = s
        }
    }

    var pathsDict: HashMap<Int, ArrayList<Path>> = HashMap<Int, ArrayList<Path>>()
    var PaintsDict: HashMap<Int, ArrayList<Paint>> = HashMap<Int, ArrayList<Paint>>()
    var PathStackDict: HashMap<Int, Stack<Path>> = HashMap<Int, Stack<Path>>()
    var PaintStackDict: HashMap<Int, Stack<Paint>> = HashMap<Int, Stack<Paint>>()
    var RedoStackDict: HashMap<Int, Stack<Job>> = HashMap<Int, Stack<Job>>()
    var UndoStackDict: HashMap<Int, Stack<Job>> = HashMap<Int, Stack<Job>>()

    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948
    var curPage = 0
    var totalPages = 3
    var pg: TextView? = null
    var pdfName: TextView? = null
    var toolbarLayout: LinearLayout? = null
    var toolbarGroup: LinearLayout? = null
    var l: LinearLayout? = null

    var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPage: PdfRenderer.Page? = null

    var pageImage: PDFimage? = null

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbarLayout = LinearLayout(this)
        toolbarGroup = RadioGroup(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val pen = StandardButton(this, "pen")
        val highlight = StandardButton(this, "brush")
        val erase = StandardButton(this, "eraser")
        val zp = StandardButton(this, "zoom")
        val redo = Button(this)
        redo.text = "redo"
        val undo = Button(this)
        undo.text = "undo"

        pdfName = TextView(this)
        pdfName!!.text = FILENAME

        var views = Arrays.asList(pen, erase, highlight, zp)
        for (i in 0..3) {
            toolbarGroup!!.addView(views.get(i))
        }

        toolbarLayout!!.addView(pdfName)
        toolbarLayout!!.addView(toolbarGroup)
        toolbarLayout!!.addView(redo)
        toolbarLayout!!.addView(undo)

        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        pageImage = PDFimage(this)

        val previous = Button(this)
        previous.text = "previous"
        previous.width = 340
        val next = Button(this)
        next.text = "next"
        next.width = 340

        l = LinearLayout(this)
        l!!.addView(previous)
        l!!.addView(next)

        pg = TextView(this)
        pg!!.text = String.format("%d / %d", curPage + 1, totalPages)

        pg!!.setPadding(8, 8, 8, 8)

        toolbarGroup!!.orientation = LinearLayout.HORIZONTAL
        toolbarGroup!!.gravity = Gravity.CENTER_HORIZONTAL
        toolbarLayout!!.orientation = LinearLayout.HORIZONTAL
        toolbarLayout!!.gravity = Gravity.CENTER_HORIZONTAL
        l!!.orientation = LinearLayout.HORIZONTAL
        l!!.gravity = Gravity.CENTER_HORIZONTAL
        pg!!.gravity = Gravity.CENTER_HORIZONTAL

        pageImage!!.minimumWidth = 1000
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            pageImage!!.minimumHeight = 2000
        } else {
            pageImage!!.minimumHeight = 1250
        }

        layout.addView(toolbarLayout)
        layout.addView(pageImage)
        layout.addView(pg)
        layout.addView(l!!)
        layout.isEnabled = true

        val penPaint = Paint()
        penPaint.color = Color.BLUE
        penPaint.style = Paint.Style.STROKE
        penPaint.strokeWidth = 4f
        penPaint.isAntiAlias = true

        pen.setOnClickListener {
            pageImage!!.setBrush(penPaint)
            pageImage!!.mode = PEN
        }

        val highlightPaint = Paint()
        highlightPaint.color = Color.YELLOW
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 40f
        highlightPaint.isAntiAlias = true
        highlightPaint.alpha = 90

        highlight.setOnClickListener {
            pageImage!!.setBrush(highlightPaint)
            pageImage!!.mode = PEN
        }

        pen.isSelected = true
        pen.isChecked = true
        previous.setOnClickListener {
            if (curPage <= 0) {
                curPage = 0
            } else {
                curPage--
            }
            changePage(curPage)
        }
        next.setOnClickListener {
            if (curPage >= totalPages - 1) {
                curPage = totalPages - 1
            } else {
                curPage++
            }
            changePage(curPage)
        }

        erase.setOnClickListener {
            pageImage!!.mode = ERASER
        }
        zp.setOnClickListener {
            pageImage!!.mode = ZOOM
        }

        redo.setOnClickListener {
            val redoTemp = RedoStackDict[curPage]
            val undoTemp = UndoStackDict[curPage]
            if (!redoTemp!!.empty()) {
                val theJob = redoTemp.pop() as Job
                doJob(theJob, true, curPage)
                undoTemp!!.push(theJob)
            }
        }
        undo.setOnClickListener {
            val redoTemp = RedoStackDict[curPage]
            val undoTemp = UndoStackDict[curPage]
            if (!undoTemp!!.empty()) {
                val theJob = undoTemp.pop() as Job
                doJob(theJob, false, curPage)
                redoTemp!!.push(theJob)
            }
        }

        try {
            openRenderer(this)
            pageImage!!.setBrush(penPaint)
            for (i in 0 until totalPages) {
                pathsDict[i] = ArrayList<Path>()
                PaintsDict[i] = ArrayList<Paint>()
                PathStackDict[i] = Stack<Path>()
                PaintStackDict[i] = Stack<Paint>()
                RedoStackDict[i] = Stack<Job>()
                UndoStackDict[i] = Stack<Job>()
            }
            changePage(curPage)

        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("on configuration chagne", "1")
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            pageImage!!.minimumHeight = 2000
        } else {
            pageImage!!.minimumHeight = 1250
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onStop() {
        super.onStop()
        try {
            closeRenderer()
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            openRenderer(this)
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to resume PDF renderer")
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                size = size
                output.write(buffer, 0, size)
            }
            size = size
            output.close()
            asset.close()
        }

        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        if (parcelFileDescriptor != null) {
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            totalPages = totalPages
            totalPages = pdfRenderer!!.pageCount
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(IOException::class)
    private fun closeRenderer() {
        if (null != currentPage) {
            currentPage!!.close()
        }
        pdfRenderer!!.close()
        parcelFileDescriptor!!.close()
    }

    private fun doJob(job: Job, isRedo: Boolean, cPage: Int) {
        val pathListTemp = pathsDict[cPage]
        val paintListTemp = PaintsDict[cPage]
        val pathStackTemp = PathStackDict[cPage]
        val paintStackTemp = PaintStackDict[cPage]
        fun redo() {
            val pathTemp = pathStackTemp!!.pop()
            val paintTemp = paintStackTemp!!.pop()
            pathListTemp!!.add(pathTemp)
            paintListTemp!!.add(paintTemp)
        }

        fun undo() {
            val pathTemp = pathListTemp!![pathListTemp.size - 1]
            val paintTemp = paintListTemp!![paintListTemp.size - 1]
            pathListTemp.removeAt(pathListTemp.size - 1)
            paintListTemp.removeAt(paintListTemp.size - 1)
            pathStackTemp!!.push(pathTemp)
            paintStackTemp!!.push(paintTemp)
        }

        if (isRedo) {
            if (job.isEraser) {
                for (i in 0 until job.number) {
                    undo()
                }
            } else {
                redo()
            }
        } else {
            if (job.isEraser) {
                for (i in 0 until job.number) {
                    redo()
                }
            } else {
                undo()
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun showPage(index: Int) {
        if (pdfRenderer?.pageCount!! <= index) {
            return
        }
        try {
            currentPage?.close()
        } catch (ex: java.lang.IllegalStateException) {
            Log.d("Showpage", "Page is already closed.")
        }
        currentPage = pdfRenderer?.openPage(index)
        val bitmap = currentPage?.let { Bitmap.createBitmap(it.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888) }
        currentPage?.render(bitmap!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pageImage!!.setImage(bitmap)
        pg!!.text = String.format("%d / %d", curPage + 1, totalPages)
    }

    private fun changePage(curPage: Int) {
        pg!!.text = String.format("%d / %d", curPage + 1, totalPages)
        pageImage!!.paths = pathsDict[curPage]!!
        pageImage!!.colors = PaintsDict[curPage]!!
        pageImage!!.pathStack = PathStackDict[curPage]!!
        pageImage!!.paintStack = PaintStackDict[curPage]!!
        pageImage!!.redoStack = RedoStackDict[curPage]!!
        pageImage!!.undoStack = UndoStackDict[curPage]!!
        showPage(curPage)
    }

    companion object {
        const val PEN = 2
        const val ERASER = 1
        const val ZOOM = 3
    }
}

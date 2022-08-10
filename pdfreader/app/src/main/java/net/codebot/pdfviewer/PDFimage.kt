package net.codebot.pdfviewer

import android.content.Context
import android.widget.ImageView
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.annotation.SuppressLint
import android.graphics.*

import java.util.*

@SuppressLint("AppCompatCustomView")
class PDFimage(var c: Context) : ImageView(c) {
    private var mPosX = 0f
    private var mPosY = 0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mLastGestureX = 0f
    private var mLastGestureY = 0f
    private var mActivePointerId = INVALID_POINTER_ID
    private val mScaleDetector: ScaleGestureDetector
    private var mScaleFactor = 1f
    var clip = Region(0, 0, 1000, 1000)
    var mode: Int = MainActivity.PEN

    // drawing path
    var path: Path = Path()
    var paths = ArrayList<Path>()
    var colors: ArrayList<Paint> = ArrayList<Paint>()
    var pathStack = Stack<Path>()
    var paintStack = Stack<Paint>()
    var redoStack = Stack<Job>()
    var undoStack = Stack<Job>()

    // image to display
    var bitmap: Bitmap? = null
    var p = Paint(Color.BLUE)

    // constructor
    init {
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val tmp = mScaleFactor * detector.scaleFactor
            mScaleFactor = Math.max(0.1f, Math.min(tmp, 10.0f))
            invalidate()
            return true
        }
    }

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (mode) {
            MainActivity.ERASER -> when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path = Path()
                    path.moveTo(event.x, event.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.x, event.y)
                }
                MotionEvent.ACTION_UP -> {
                    var counter = 0
                    var i = 0
                    while (i < paths.size) {
                        val region1 = Region()
                        region1.setPath(path, clip)
                        val region2 = Region()
                        region2.setPath(paths[i], clip)
                        if (!region1.quickReject(region2) && region1.op(region2, Region.Op.INTERSECT)) {
                            pathStack.push(paths[i])
                            paintStack.push(colors[i])
                            paths.removeAt(i)
                            colors.removeAt(i)
                            i--
                            counter++
                        }
                        i++
                    }
                    undoStack.push(Job(counter, true))
                }
            }
            MainActivity.PEN -> when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path = Path()
                    path.moveTo(event.x, event.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.x, event.y)
                }
                MotionEvent.ACTION_UP -> {
                    paths.add(path)
                    colors.add(p)
                    undoStack.push(Job(1, false))
                }
            }
            MainActivity.ZOOM -> {
                mScaleDetector.onTouchEvent(event)
                val action = event.action
                when (action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!mScaleDetector.isInProgress) {
                            val x = event.x
                            val y = event.y
                            mLastTouchX = x
                            mLastTouchY = y
                            mActivePointerId = event.getPointerId(0)
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        mLastGestureX = mScaleDetector.focusX
                        mLastGestureY = mScaleDetector.focusY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!mScaleDetector.isInProgress) {
                            val pointerIndex = event.findPointerIndex(mActivePointerId)
                            val x = event.getX(pointerIndex)
                            val y = event.getY(pointerIndex)
                            val dx = x - mLastTouchX
                            val dy = y - mLastTouchY
                            mPosX += dx
                            mPosY += dy
                            invalidate()
                            mLastTouchX = x
                            mLastTouchY = y
                        } else {
                            val gx = mScaleDetector.focusX
                            val gy = mScaleDetector.focusY
                            val gdx = gx - mLastGestureX
                            val gdy = gy - mLastGestureY
                            mPosX += gdx
                            mPosY += gdy
                            invalidate()
                            mLastGestureX = gx
                            mLastGestureY = gy
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mActivePointerId = INVALID_POINTER_ID
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val pointerIndex = (event.action and MotionEvent.ACTION_POINTER_INDEX_MASK
                                shr MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                        val pointerId = event.getPointerId(pointerIndex)
                        if (pointerId == mActivePointerId) {
                            val newPointerIndex = if (pointerIndex == 0) 1 else 0
                            mLastTouchX = event.getX(newPointerIndex)
                            mLastTouchY = event.getY(newPointerIndex)
                            mActivePointerId = event.getPointerId(newPointerIndex)
                        } else {
                            val tempPointerIndex = event.findPointerIndex(mActivePointerId)
                            mLastTouchX = event.getX(tempPointerIndex)
                            mLastTouchY = event.getY(tempPointerIndex)
                        }
                    }
                }
            }
        }
        return true
    }

    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    fun setBrush(paint: Paint) {
        p = paint
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()

        if (bitmap != null) {
            setImageBitmap(bitmap)
        }
        val scaleMatrix = Matrix()
        val translateMatrix = Matrix()

        translateMatrix.setTranslate(mPosX, mPosY)

        var px: Float
        var py: Float
        if (!mScaleDetector.isInProgress) {
            px = mLastGestureX
            py = mLastGestureY
        } else {
            px = mScaleDetector.focusX
            py = mScaleDetector.focusY
        }
        scaleMatrix.setScale(mScaleFactor, mScaleFactor, px, py)

        for (i in paths.indices) {
            val temp = Path(paths[i])
            temp.transform(scaleMatrix)
            temp.transform(translateMatrix)
            canvas.drawPath(temp, colors[i])
        }

        canvas.translate(mPosX, mPosY)
        if (!mScaleDetector.isInProgress) {
            canvas.scale(mScaleFactor, mScaleFactor, mLastGestureX, mLastGestureY)
        } else {
            canvas.scale(mScaleFactor, mScaleFactor, mScaleDetector.focusX, mScaleDetector.focusY)
        }
        super.onDraw(canvas)
        canvas.restore()
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
    }
}
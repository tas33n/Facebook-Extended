package tn.loukious.facebookappadsremover.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import tn.loukious.facebookappadsremover.core.Settings
import tn.loukious.facebookappadsremover.core.L
import kotlin.math.abs

/** Opens only pixels already rendered in this app window. No URL/ID queries. */
internal class ProfilePhotoPreview(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var callback: Window.Callback? = null
    private var delegate: Window.Callback? = null
    private var photos = emptyList<Photo>()
    private var viewer: Dialog? = null
    private var down: Photo? = null
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var capturing = false
    private var lastReport = ""
    private data class Photo(val label: String, val bounds: Rect, val captureBounds: Rect = Rect(bounds))
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            runCatching { inspect() }.onFailure { photos = emptyList() }
            handler.postDelayed(this, 550)
        }
    }
    fun start() {
        if (active) return
        active = true
        bindCallback()
        handler.post(tick)
    }
    private fun bindCallback() {
        val original = activity.window.callback ?: return
        delegate = original
        callback = object : Window.Callback by original {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (enabled() && viewer == null && !capturing) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX; startY = event.rawY; downTime = event.eventTime
                            down = photos.firstOrNull { it.bounds.contains(startX.toInt(), startY.toInt()) }
                            if (down == null) tn.loukious.facebookappadsremover.hooks.ProfilePhotoSources.clear()
                        }
                        MotionEvent.ACTION_MOVE -> if (abs(event.rawX-startX) > slop() || abs(event.rawY-startY) > slop()) down = null
                        MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> down = null
                        MotionEvent.ACTION_UP -> {
                            val hit = down; down = null
                            if (hit != null && event.eventTime - downTime < ViewConfiguration.getLongPressTimeout() &&
                                photos.any { it == hit } && hit.bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                                try { original.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                                capture(hit)
                                return true
                            }
                        }
                    }
                } else down = null
                return original.dispatchTouchEvent(event)
            }
        }
        activity.window.callback = callback
    }
    fun stop() {
        active = false; handler.removeCallbacks(tick); photos = emptyList(); down = null
        if (activity.window.callback === callback) activity.window.callback = delegate
        callback = null; delegate = null; viewer?.dismiss(); viewer = null
    }
    private fun slop() = ViewConfiguration.get(activity).scaledTouchSlop
    private fun enabled() = Settings.getBoolean(Settings.PRIVACY_OPEN_PHOTO_PREVIEWS, false)
    private fun inspect() {
        if (!enabled() || !activity.hasWindowFocus() || viewer != null) { photos = emptyList(); return }
        if (activity.window.callback !== callback) bindCallback()
        val root = activity.window.decorView
        val candidates = ArrayList<Photo>(); val blockers = ArrayList<Rect>()
        var locked = false; var profile = false; var budget = 600
        fun node(label: String, bounds: Rect, clickable: Boolean) {
            if (label.contains("locked", true) && label.contains("profile", true)) locked = true
            if (label == "More profile settings") profile = true
            if ((label == "Profile picture" || label == "Cover Photo") && !clickable && !bounds.isEmpty)
                candidates.add(Photo(label, Rect(bounds)))
            else if (clickable && bounds.width() > 0 && bounds.height() > 0 && bounds.height() < root.height / 4)
                blockers.add(Rect(bounds))
        }
        val queue = java.util.ArrayDeque<View>(); queue.add(root); var views = 0
        while (queue.isNotEmpty() && views++ < 1400) {
            val view = queue.removeFirst(); if (!view.isShown) continue
            val rect = Rect(); if (!view.getGlobalVisibleRect(rect)) continue
            node(view.contentDescription?.toString().orEmpty(), rect, view.isClickable)
            // Facebook accessibility delegates populate labels only when creating the node.
            if (rect.top < root.height / 3 && view !is android.widget.ScrollView) {
                val info = runCatching { view.createAccessibilityNodeInfo() }.getOrNull()
                if (info != null) {
                    try { val b=Rect();info.getBoundsInScreen(b);node(info.contentDescription?.toString().orEmpty(),b,info.isClickable) }
                    finally { @Suppress("DEPRECATION") info.recycle() }
                }
            }
            if (view is TextView && view.text.toString().contains("locked", true)) node(view.text.toString(),rect,view.isClickable)
            val provider = if (budget > 0) view.accessibilityNodeProvider else null
            if (provider != null) {
                val pending = java.util.ArrayDeque<AccessibilityNodeInfo>();val seen=HashSet<Int>()
                provider.createAccessibilityNodeInfo(View.NO_ID)?.let(pending::add)
                while (pending.isNotEmpty() && budget-- > 0) {
                    val info=pending.removeFirst()
                    try {
                        val b=Rect();info.getBoundsInScreen(b)
                        if (info.isVisibleToUser) node(info.contentDescription?.toString() ?: info.text?.toString().orEmpty(),b,info.isClickable)
                        for (i in 0 until info.childCount.coerceAtMost(40)) {
                            val id=runCatching { (childId?.invoke(info,i) as? Long)?.ushr(32)?.toInt() }.getOrNull()
                            if(id!=null && seen.add(id)) provider.createAccessibilityNodeInfo(id)?.let(pending::add)
                        }
                    } finally { @Suppress("DEPRECATION") info.recycle() }
                }
                while(pending.isNotEmpty()){ @Suppress("DEPRECATION") pending.removeFirst().recycle() }
            }
            if(view is ViewGroup) for(i in 0 until view.childCount) queue.add(view.getChildAt(i))
        }
        photos = if (locked && profile) candidates.distinctBy { it.label }.sortedBy { it.bounds.width()*it.bounds.height() }.mapNotNull { photo ->
            val b=Rect(photo.bounds)
            // Never intercept the header's Back/Search/More controls over the cover.
            if(photo.label=="Cover Photo") for(block in blockers) {
                if(Rect.intersects(b,block) && block.top <= b.top + b.height()/2 && block.bottom < b.bottom) b.top=maxOf(b.top,block.bottom)
            }
            if(b.height()<48 || b.width()<48) null else Photo(photo.label,b,Rect(photo.captureBounds))
        } else emptyList()
        val report="locked=$locked profile=$profile candidates=${candidates.size} targets=${photos.size} callback=${activity.window.callback === callback}"
        if(report!=lastReport){lastReport=report;L.i("FBAR.PhotoPreview",report)}
    }
    private fun capture(photo: Photo) {
        val url=tn.loukious.facebookappadsremover.hooks.ProfilePhotoSources.url(photo.label=="Cover Photo")
        if(url==null){Toast.makeText(activity,"Source unavailable. Reopen this profile to refresh its image data.",Toast.LENGTH_LONG).show();return}
        capturing=true
        Toast.makeText(activity,"Loading available image…",Toast.LENGTH_SHORT).show()
        Thread {
            val bitmap=runCatching {
                val request=okhttp3.Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful)
                    val body=response.body ?: error("No image")
                    check(body.contentType()?.type=="image")
                    check(body.contentLength()<=20*1024*1024)
                    val bytes=body.byteStream().use { stream ->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val n=stream.read(buffer);if(n<0)break;check(out.size()+n<=20*1024*1024);out.write(buffer,0,n)}
                        out.toByteArray()
                    }
                    val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    check(bounds.outWidth>0&&bounds.outHeight>0)
                    var sample=1
                    while(bounds.outWidth.toLong()*bounds.outHeight/(sample.toLong()*sample)>12_000_000)sample*=2
                    android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})
                }
            }.getOrNull()
            handler.post {
                capturing=false
                if(bitmap!=null && active && !activity.isFinishing && enabled() &&
                    tn.loukious.facebookappadsremover.hooks.ProfilePhotoSources.url(photo.label=="Cover Photo")==url) show(photo.label,bitmap)
                else {bitmap?.recycle();if(active)Toast.makeText(activity,"Facebook's supplied image could not be opened. No screen capture was used.",Toast.LENGTH_LONG).show()}
            }
        }.apply {name="FBAR-PhotoSource"}.start()
    }
    private fun show(label:String,bitmap:Bitmap) {
        val dialog=Dialog(activity,android.R.style.Theme_Material_NoActionBar)
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK) }
        val image=ZoomImage(activity) { ImageActions.show(activity,bitmap) }.apply { setImageBitmap(bitmap) }
        body.addView(image,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        body.addView(Button(activity).apply { text="Close";setOnClickListener { dialog.dismiss() } })
        dialog.setContentView(body); viewer=dialog
        dialog.setOnDismissListener { image.setImageDrawable(null);bitmap.recycle();viewer=null }
        dialog.show();dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        L.i("FBAR.PhotoSource","SOURCE_OPEN kind=${if(label=="Profile picture") "profile" else "cover"} width=${bitmap.width} height=${bitmap.height}")
    }
    private class ZoomImage(activity:Activity, private val longPress: () -> Unit):ImageView(activity) {
        private val transform=Matrix();private var zoom=1f;private var x=0f;private var y=0f
        private var pointer = -1
        private val detector=ScaleGestureDetector(activity,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(d:ScaleGestureDetector):Boolean {
                val next=(zoom*d.scaleFactor).coerceIn(1f,6f)
                transform.postScale(next/zoom,next/zoom,d.focusX,d.focusY);zoom=next
                constrain();return true
            }
        })
        private val taps=GestureDetector(activity,object:GestureDetector.SimpleOnGestureListener(){
            override fun onDown(e:MotionEvent)=true
            override fun onLongPress(e:MotionEvent) { if(!detector.isInProgress) longPress() }
            override fun onDoubleTap(e:MotionEvent):Boolean {
                if(zoom>1.05f) reset() else {transform.postScale(2.5f,2.5f,e.x,e.y);zoom=2.5f;constrain()}
                return true
            }
        })
        init { scaleType=ScaleType.MATRIX }
        private fun reset(){
            val d=drawable?:return
            if(width<=0 || height<=0)return
            transform.setRectToRect(android.graphics.RectF(0f,0f,d.intrinsicWidth.toFloat(),d.intrinsicHeight.toFloat()),android.graphics.RectF(0f,0f,width.toFloat(),height.toFloat()),Matrix.ScaleToFit.CENTER)
            zoom=1f;imageMatrix=transform
        }
        private fun constrain(){
            val d=drawable?:return
            val bounds=android.graphics.RectF(0f,0f,d.intrinsicWidth.toFloat(),d.intrinsicHeight.toFloat());transform.mapRect(bounds)
            val dx=PreviewPanBounds.correction(bounds.left,bounds.right,width.toFloat())
            val dy=PreviewPanBounds.correction(bounds.top,bounds.bottom,height.toFloat())
            transform.postTranslate(dx,dy);imageMatrix=transform
        }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);reset()}
        override fun onTouchEvent(e:MotionEvent):Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            detector.onTouchEvent(e);taps.onTouchEvent(e)
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{pointer=e.getPointerId(0);x=e.x;y=e.y}
                MotionEvent.ACTION_POINTER_DOWN->{pointer=-1}
                MotionEvent.ACTION_POINTER_UP->{val next=if(e.actionIndex==0)1 else 0;pointer=e.getPointerId(next);x=e.getX(next);y=e.getY(next)}
                MotionEvent.ACTION_MOVE->{
                    val i=e.findPointerIndex(pointer)
                    if(i>=0){val nx=e.getX(i);val ny=e.getY(i)
                        if(!detector.isInProgress && e.pointerCount==1 && zoom>1){transform.postTranslate(nx-x,ny-y);constrain()}
                        x=nx;y=ny
                    }
                }
                MotionEvent.ACTION_UP->{pointer=-1;performClick()}
                MotionEvent.ACTION_CANCEL->pointer=-1
            };return true
        }
        override fun performClick():Boolean {super.performClick();return true}
    }
    companion object {
        private val client=okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build()
        private val childId=runCatching {AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId",Int::class.javaPrimitiveType).apply {isAccessible=true}}.getOrNull()
    }
}

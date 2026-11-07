package tn.loukious.facebookappadsremover.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import tn.loukious.facebookappadsremover.core.Settings
import tn.loukious.facebookappadsremover.hooks.DownloadHook
import tn.loukious.facebookappadsremover.core.L

/** UI entry points bind to a selected menu link or a typed visible player, never recency. */
internal class VideoDownloadEntry(private val activity: Activity) {
    private val main=Handler(Looper.getMainLooper())
    private var active=false
    private var button:TextView?=null
    private var lastVideoMenu=0L
    private var boundId:String?=null
    private var lastScan=0L
    private var queued=false
    private var requestGeneration=0
    private var shareRequestUntil=0L
    private val signal:()->Unit = { schedule() }
    private val layout=android.view.ViewTreeObserver.OnGlobalLayoutListener { schedule() }
    private val scroll=android.view.ViewTreeObserver.OnScrollChangedListener { schedule() }
    private fun schedule() {
        if(!active || queued)return
        queued=true
        main.postDelayed({queued=false;if(active){runCatching {update()}.onFailure {hide()}}},
            (180-(android.os.SystemClock.elapsedRealtime()-lastScan)).coerceAtLeast(0))
    }
    private data class Action(val text:String,val bounds:Rect,val click:()->Boolean)
    private val tick=object:Runnable {override fun run(){if(!active)return;schedule();main.postDelayed(this,if(shareRequestUntil>android.os.SystemClock.elapsedRealtime())180 else 1500)}}
    fun start(){if(active)return;active=true;VideoDownloadSignals.add(signal);activity.window.decorView.viewTreeObserver.apply {addOnGlobalLayoutListener(layout);addOnScrollChangedListener(scroll)};main.post(tick)}
    fun stop(){active=false;requestGeneration++;main.removeCallbacksAndMessages(null);queued=false;VideoDownloadSignals.remove(signal);activity.window.decorView.viewTreeObserver.apply {if(isAlive){removeOnGlobalLayoutListener(layout);removeOnScrollChangedListener(scroll)}};button?.let {(it.parent as? ViewGroup)?.removeView(it)};button=null}
    private fun currentVisibleId(root:FrameLayout):String? {
        val visible=Rect();if(!root.getGlobalVisibleRect(visible))return null
        val queue=java.util.ArrayDeque<View>();queue.add(root)
        val ids=ArrayList<String>();var scanned=0;var players=0
        while(queue.isNotEmpty()&&scanned++<1400){
            val view=queue.removeFirst();if(view===button||!view.isShown)continue
            val bounds=Rect();if(!view.getGlobalVisibleRect(bounds))continue
            val name=view.javaClass.name
            // Exclude mostly-offscreen prefetched neighbours in the vertical pager.
            if(players<32 && bounds.height()>visible.height()/2 &&
                bounds.contains(visible.centerX(),visible.centerY()) &&
                (name.startsWith("X.")||name.contains("RichVideo")||name.contains("VideoPlayer"))){
                players++;DownloadHook.visiblePlayerId(view)?.let(ids::add)
            }
            if(view is ViewGroup)for(i in 0 until view.childCount)queue.add(view.getChildAt(i))
        }
        val selected=DownloadHook.selectVisibleVideo(ids)
        L.i("Extended.Download","VISIBLE_BIND candidates=${ids.distinct().size} selected=${selected!=null}")
        return selected
    }
    private fun downloadCurrent(root:FrameLayout){
        val generation=++requestGeneration
        fun attempt(left:Int){
            if(!active||generation!=requestGeneration||!root.hasWindowFocus())return
            val id=tn.loukious.facebookappadsremover.hooks.PlayingReelSource.currentId() ?: currentVisibleId(root)
            if(id!=null){
                DownloadHook.openVisibleDownload(activity,id) {
                    active && generation==requestGeneration &&
                        (tn.loukious.facebookappadsremover.hooks.PlayingReelSource.currentId() ?: currentVisibleId(root))==id
                }
            } else if(left>0)main.postDelayed({attempt(left-1)},180)
            else android.widget.Toast.makeText(activity,"Could not identify this Reel. Use its menu → Download video.",android.widget.Toast.LENGTH_LONG).show()
        }
        attempt(3)
    }
    private fun hide(){button?.visibility=View.GONE;boundId=null}
    private fun roots():List<FrameLayout> = runCatching {
        val c=Class.forName("android.view.WindowManagerGlobal")
        val global=c.getDeclaredMethod("getInstance").invoke(null)
        val views=c.getDeclaredField("mViews").apply {isAccessible=true}.get(global) as List<*>
        views.filterIsInstance<FrameLayout>().filter {it.isShown && it.hasWindowFocus()}
    }.getOrDefault(listOfNotNull(activity.window.decorView as? FrameLayout))
    private fun update(){
        lastScan=android.os.SystemClock.elapsedRealtime()
        if(!Settings.getBoolean(Settings.DOWNLOAD_SHOW_ICON,true)){hide();return}
        val root=roots().lastOrNull() ?: return hide()
        val actions=ArrayList<Action>();val queue=java.util.ArrayDeque<View>();queue.add(root)
        var budget=350;var count=0;val players=ArrayList<View>()
        fun add(text:String?,b:Rect,click:()->Boolean){if(!text.isNullOrBlank()&&!b.isEmpty)actions.add(Action(text.trim(),Rect(b),click))}
        while(queue.isNotEmpty()&&count++<1400){
            val v=queue.removeFirst();if(!v.isShown||v===button)continue
            val b=Rect();if(!v.getGlobalVisibleRect(b))continue
            val name=v.javaClass.name
            if(b.height()>root.height/3 && (name.contains("RichVideo")||name.contains("VideoPlayer")||name.startsWith("X.")))players.add(v)
            val node=runCatching {v.createAccessibilityNodeInfo()}.getOrNull()
            if(node!=null){try {add(node.contentDescription?.toString()?:node.text?.toString(),b){v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null)}} finally {@Suppress("DEPRECATION") node.recycle()}}
            val provider=if(budget>0)v.accessibilityNodeProvider else null
            if(provider!=null){
                val pending=java.util.ArrayDeque<Pair<Int,AccessibilityNodeInfo>>();val seen=HashSet<Int>()
                provider.createAccessibilityNodeInfo(View.NO_ID)?.let {pending.add(View.NO_ID to it)}
                while(pending.isNotEmpty()&&budget-->0){val(id,n)=pending.removeFirst();try {
                    val rect=Rect();n.getBoundsInScreen(rect)
                    if(n.isVisibleToUser)add(n.contentDescription?.toString()?:n.text?.toString(),rect){provider.performAction(id,AccessibilityNodeInfo.ACTION_CLICK,null)}
                    for(i in 0 until n.childCount.coerceAtMost(40)){
                        val child=runCatching {(childId?.invoke(n,i) as? Long)?.ushr(32)?.toInt()}.getOrNull()
                        if(child!=null&&seen.add(child))provider.createAccessibilityNodeInfo(child)?.let {pending.add(child to it)}
                    }
                }finally {@Suppress("DEPRECATION") n.recycle()}}
                while(pending.isNotEmpty()){@Suppress("DEPRECATION") pending.removeFirst().second.recycle()}
            }
            if(v is ViewGroup)for(i in 0 until v.childCount)queue.add(v.getChildAt(i))
        }
        val now=android.os.SystemClock.elapsedRealtime()
        val videoMenu=actions.any {it.text.equals("Save reel",true)||it.text.equals("Report reel",true)||it.text.equals("Cast reel",true)||it.text.equals("Save video",true)}
        if(videoMenu)lastVideoMenu=now
        val copy=actions.firstOrNull {it.text=="Copy link"}
        if(shareRequestUntil>0){
            if(now>shareRequestUntil){
                shareRequestUntil=0
                android.widget.Toast.makeText(activity,"Could not open this Reel's link action. Please use Copy link in its Share panel.",android.widget.Toast.LENGTH_LONG).show()
            } else if(copy!=null){
                shareRequestUntil=0
                DownloadHook.armMenuDownload()
                val clicked=copy.click()
                L.i("Extended.Download","REEL_SELECTED_LINK dispatched=$clicked")
                if(!clicked)android.widget.Toast.makeText(activity,"Tap Copy link to open download options.",android.widget.Toast.LENGTH_SHORT).show()
                return
            } else {main.postDelayed({schedule()},180);return}
        }
        if(copy!=null && (videoMenu || now-lastVideoMenu<8000)){
            // Reuses the selected post's own link action. A short-lived clipboard interception
            // diverts that result to the downloader without reading or replacing prior clipboard.
            attach(root,copy.bounds,"↓   Download video",false){DownloadHook.armMenuDownload();copy.click()}
            return
        }
        val largeVideoSurface=actions.any {
            val label=it.text.lowercase(java.util.Locale.ROOT)
            (label.contains("video controls") || label=="reel details" || label=="video player") &&
                it.bounds.width()>root.width*0.65 && it.bounds.height()>root.height*0.55
        }
        val immersive=activity.javaClass.name.contains("ImmersiveActivity") || activity.javaClass.name.contains("VideoPlayerActivity")
        val fullPlayer=FullscreenVideoPolicy.isPlayer(actions.map {it.text},largeVideoSurface,immersive,
            immersive && tn.loukious.facebookappadsremover.hooks.PlayingReelSource.currentId()!=null)
        val like=actions.firstOrNull {it.bounds.left>root.width*0.7 && it.text.startsWith("Like button",true)}
            ?: actions.firstOrNull {it.bounds.left>root.width*0.7 && (it.text.equals("Like",true)||it.text.contains("react",true))}
        if(fullPlayer){
            // Render the action without waiting for a media capture. Resolve the visible
            // typed player again on tap to avoid an old Reel after a swipe.
                val size=(like?.bounds?.width() ?: dp(44)).coerceAtLeast(dp(44))
                val origin=IntArray(2);root.getLocationOnScreen(origin)
                val rect=if(like!=null) Rect(like.bounds.left,like.bounds.top-size-dp(10),like.bounds.left+size,like.bounds.top-dp(10))
                    else Rect(origin[0]+root.width-size-dp(16),origin[1]+dp(96),origin[0]+root.width-dp(16),origin[1]+dp(96)+size)
                val blocked=actions.any {it.bounds!=like?.bounds && it.bounds.width()<root.width/2 &&
                    it.bounds.height()<root.height/3 && Rect.intersects(rect,it.bounds)}
                if(rect.top>origin[1]+dp(64) && (!blocked || like!=null)){
                    attach(root,rect,"↓",true){
                        downloadCurrent(root)
                    };return
                }
        }
        hide()
    }
    private fun attach(root:FrameLayout,bounds:Rect,label:String,reel:Boolean,click:()->Unit){
        val b=button ?: TextView(activity).apply {gravity=android.view.Gravity.CENTER_VERTICAL;isClickable=true;isFocusable=true}.also {button=it}
        if(b.parent!==root){(b.parent as? ViewGroup)?.removeView(b);root.addView(b,FrameLayout.LayoutParams(bounds.width(),bounds.height()))}
        val loc=IntArray(2);root.getLocationOnScreen(loc)
        if(b.layoutParams.width!=bounds.width() || b.layoutParams.height!=bounds.height())b.layoutParams=FrameLayout.LayoutParams(bounds.width(),bounds.height())
        b.x=(bounds.left-loc[0]).toFloat();b.y=(bounds.top-loc[1]).toFloat()
        if(b.text.toString()!=label){b.text=label;b.textSize=if(reel)32f else 15f}
        b.contentDescription="Download video"
        b.gravity=if(reel)android.view.Gravity.CENTER else android.view.Gravity.CENTER_VERTICAL
        val padding=if(reel)0 else dp(16);if(b.paddingLeft!=padding)b.setPadding(padding,0,0,0)
        b.setTextColor(if(reel)Color.WHITE else Color.BLACK);b.setBackgroundColor(if(reel)Color.TRANSPARENT else 0xfff0f2f5.toInt())
        b.setOnClickListener {click()};b.visibility=View.VISIBLE;b.bringToFront()
    }
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+0.5f).toInt()
    companion object {private val childId=runCatching {AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId",Int::class.javaPrimitiveType).apply {isAccessible=true}}.getOrNull()}
}

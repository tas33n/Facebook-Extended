package tn.loukious.facebookappadsremover.hooks

import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap
import java.lang.reflect.Method
import tn.loukious.facebookappadsremover.core.HostCompatibility

/** 585-specific player observer; reads the live params, never a prefetched recent ID. */
object PlayingReelSource {
    private var installed=false
    private val players=WeakHashMap<Any,Boolean>()
    private var params:Method?=null
    private var playing:Method?=null
    fun install(module:XposedInterface,loader:ClassLoader){
        if(installed)return
        runCatching {
            val cls=Class.forName(HostCompatibility.PLAYER_CLASS,false,loader)
            val getter=cls.declaredMethods.single {it.parameterCount==0&&it.returnType.name=="com.facebook.video.engine.api.VideoPlayerParams"}
            val isPlaying=cls.declaredMethods.single {it.name=="isPlaying"&&it.parameterCount==0&&it.returnType==Boolean::class.javaPrimitiveType}
            getter.isAccessible=true;isPlaying.isAccessible=true
            params=getter;playing=isPlaying
            for(name in listOf("maybeTrackVideoStart","maybeTrackVideoStop")){
                val method=cls.declaredMethods.single {it.name==name&&it.parameterCount==1}
                module.hook(method).intercept(object:XposedInterface.Hooker{
                    override fun intercept(chain:XposedInterface.Chain):Any? {
                        val result=chain.proceed()
                        chain.thisObject?.let {synchronized(players){players[it]=true}}
                        tn.loukious.facebookappadsremover.ui.VideoDownloadSignals.notifyChanged()
                        return result
                    }
                })
            }
            installed=true
            android.util.Log.i("Extended.Download","LIVE_PLAYER_OBSERVER installed")
        }
    }
    fun currentId():String? {
        val candidates=synchronized(players){players.keys.toList()}
        val ids=candidates.mapNotNull { player -> runCatching {
            if(playing?.invoke(player)!=true)return@runCatching null
            params?.invoke(player)?.toString()?.removePrefix("VideoId: ")?.takeIf {it.matches(Regex("[0-9]{5,25}"))}
        }.getOrNull() }.distinct()
        android.util.Log.i("Extended.Download","LIVE_PLAYER_BIND playing=${ids.size}")
        return ids.singleOrNull()
    }
}

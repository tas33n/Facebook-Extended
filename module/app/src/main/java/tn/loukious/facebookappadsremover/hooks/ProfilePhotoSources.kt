package tn.loukious.facebookappadsremover.hooks

import android.os.SystemClock
import io.github.libxposed.api.XposedInterface
import java.net.URI
import android.util.Log
import tn.loukious.facebookappadsremover.core.HostCompatibility

/** Exact URLs from the active header render; no URL rewriting, cookies or ID queries. */
object ProfilePhotoSources {
    private data class Source(val profile: String?, val cover: String?, val at: Long)
    @Volatile private var current: Source? = null
    private var installed = false
    fun clear() { current = null }
    fun url(cover: Boolean): String? = current?.takeIf { SystemClock.elapsedRealtime()-it.at < 120000 }?.let { if(cover) it.cover else it.profile }
    fun allowed(value: String?): String? = runCatching {
        if(value.isNullOrBlank()) return null
        val u=URI(value);val host=u.host?.lowercase() ?: return null
        value.takeIf { u.scheme=="https" && u.userInfo==null &&
            (host.endsWith(".fbcdn.net") || host.endsWith(".fbsbx.com")) }
    }.getOrNull()
    private fun field(o:Any?,n:String):Any?=o?.javaClass?.getDeclaredField(n)?.apply {isAccessible=true}?.get(o)
    private fun get(o:Any?,n:String):Any?=o?.javaClass?.methods?.firstOrNull {it.name==n&&it.parameterCount==0}?.apply {isAccessible=true}?.invoke(o)
    private fun path(o:Any?,vararg names:String):Any? {var x=o;for(n in names)x=get(x,n);return x}
    fun install(module:XposedInterface,loader:ClassLoader){
        if(installed || android.app.Application.getProcessName()!=tn.loukious.facebookappadsremover.BuildConfig.TARGET_PACKAGE)return
        runCatching {
            val cls=Class.forName(HostCompatibility.HEADER_CLASS,false,loader)
            val render=cls.declaredMethods.single {it.name=="render"&&it.parameterCount==1}
            val imageHelper=Class.forName(HostCompatibility.IMAGE_HELPER_CLASS,false,loader).declaredMethods.single {it.name==HostCompatibility.IMAGE_HELPER_METHOD&&it.parameterCount==1}
            imageHelper.isAccessible=true
            module.hook(render).intercept(object:XposedInterface.Hooker{
                override fun intercept(chain:XposedInterface.Chain):Any?{
                    val result=chain.proceed()
                    runCatching {
                        val owner=chain.thisObject;val model=get(field(owner,HostCompatibility.HEADER_MODEL_FIELD),HostCompatibility.MODEL_GETTER)
                        val profile=allowed(imageHelper.invoke(null,model) as? String) ?: allowed(field(owner,HostCompatibility.LOW_RES_FIELD) as? String)
                        val cover=allowed(path(model,*HostCompatibility.COVER_PATH) as? String)
                        current=Source(profile,cover,SystemClock.elapsedRealtime())
                        Log.i("FBAR.PhotoSource","HEADER_SOURCE profile=${profile!=null} cover=${cover!=null}")
                    }.onFailure { current=null }
                    return result
                }
            });installed=true
        }
    }
}

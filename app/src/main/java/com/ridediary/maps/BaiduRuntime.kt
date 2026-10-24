package com.ridediary.maps

import android.content.Context
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import kotlinx.coroutines.flow.MutableStateFlow

/** The recording service never touches this object. Initialization requires an enabled, configured map. */
object BaiduRuntime {
    val authError=MutableStateFlow(false)
    private var initialized=false
    private var configuredKey=""
    fun ensure(context: Context,config: MapConfig): Boolean {
        if(!config.ready) return false
        return runCatching {
            authError.value=false
            SDKInitializer.setAgreePrivacy(context.applicationContext,true)
            if(configuredKey!=config.apiKey) {
                authError.value=false
                SDKInitializer.setApiKey(config.apiKey)
                configuredKey=config.apiKey
            }
            if(!initialized) {
                SDKInitializer.setDebugMode(false)
                SDKInitializer.addAuthResultListener { code,_ -> if(code!=-1) authError.value=code!=0 }
                SDKInitializer.initialize(context.applicationContext)
                SDKInitializer.setCoordType(CoordType.BD09LL)
                initialized=true
            }
            true
        }.getOrDefault(false)
    }
    fun revoke(context: Context) {
        if(initialized) runCatching { SDKInitializer.setAgreePrivacy(context.applicationContext,false) }
    }
}

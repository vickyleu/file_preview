package com.gstory.file_preview

import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import com.gstory.file_preview.utils.FileUtils
import com.tencent.tbs.reader.TbsFileInterfaceImpl
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler


/** FilePreviewPlugin */
class FilePreviewPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var applicationContext: Context
    private lateinit var channel: MethodChannel
    private lateinit var mFlutterPluginBinding: FlutterPlugin.FlutterPluginBinding

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if(::mFlutterPluginBinding.isInitialized){
            mFlutterPluginBinding.platformViewRegistry.registerViewFactory(
                "com.gstory.file_preview/filePreview",
                FilePreviewFactory(mFlutterPluginBinding.binaryMessenger, binding.activity)
            )
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onDetachedFromActivity() {
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "file_preview")
        channel.setMethodCallHandler(this)

        applicationContext = flutterPluginBinding.applicationContext
        mFlutterPluginBinding = flutterPluginBinding
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "initTBS" -> {
                if (::applicationContext.isInitialized) {
                    val license = call.argument<String>("license")
                    TbsFileInterfaceImpl.setLicenseKey(license)
                    TbsFileInterfaceImpl.fileEnginePreCheck(applicationContext)
                    val isInit = TbsFileInterfaceImpl.initEngine(applicationContext)
                    Log.d("=====>", "初始化 $isInit")
                    result.success(isInit == 0)
                } else {
                    result.error("1001", "上下文未初始化", "")
                }
            }

            "tbsHasInit" -> {
                if (::applicationContext.isInitialized) {
                    val ret = TbsFileInterfaceImpl.initEngine(applicationContext)
                    result.success(ret == 0)
                } else {
                    result.success(false)
                }
            }

            "tbsVersion" -> {
                result.success(TbsFileInterfaceImpl.getVersionName())
            }

            "deleteCache" -> {
                if (::applicationContext.isInitialized) {
                    FileUtils.deleteCache(applicationContext, FileUtils.getDir(applicationContext))
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

}

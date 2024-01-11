package com.gstory.file_preview

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.gstory.file_preview.utils.UIUtils
import com.gstory.file_preview.utils.FileUtils
import com.tencent.tbs.reader.ITbsReader
import com.tencent.tbs.reader.TbsFileInterfaceImpl
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.io.File


/**
 * @Author: gstory
 * @CreateDate: 2021/12/27 10:34 上午
 * @Description: 描述
 */

internal class FilePreview(
    var activity: Activity,
    messenger: BinaryMessenger,
    id: Int,
    params: Map<String?, Any?>
) :
    PlatformView, MethodChannel.MethodCallHandler {

    private val TAG = "FilePreview"

    private var mContainer: FrameLayout = FrameLayout(activity)
    private var width: Double = params["width"] as Double
    private var height: Double = params["height"] as Double
    private var path: String = params["path"] as String

    private var channel: MethodChannel?

    init {
        mContainer.layoutParams?.width = (UIUtils.dip2px(activity, width.toFloat())).toInt()
        mContainer.layoutParams?.height = (UIUtils.dip2px(activity, height.toFloat())).toInt()
        mContainer.setBackgroundColor(Color.parseColor("#FFFFFF"));
        channel = MethodChannel(messenger, "com.gstory.file_preview/filePreview_$id")
        channel?.setMethodCallHandler(this)
        loadFile(path)
    }

    override fun getView(): View {
        return mContainer
    }


    private fun loadFile(filePath: String) {
        mContainer.removeAllViews()
        //tbs只能加载本地文件 如果是网络文件则先下载
        if (filePath.startsWith("http")) {
            //进度条
            val progressBar = ProgressBar(activity)
            progressBar.setBackgroundColor(Color.RED)
            Log.wtf("progressBar", "http")
            try {
                progressBar.indeterminateDrawable =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        activity.getDrawable(R.drawable.progressbar_style)
                    } else {
                        activity.resources.getDrawable(R.drawable.progressbar_style)
                    }
                mContainer.addView(progressBar)
            } catch (e: Exception) {
            }
            val mRateText = TextView(activity)
            try {
                //文字
                mRateText.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
                mRateText.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                mRateText.gravity = Gravity.CENTER
                mRateText.setTextColor(Color.parseColor("#cccccc"))
                mRateText.textSize = 12f
                mContainer.addView(mRateText)
            } catch (e: Exception) {
            }


            FileUtils.downLoadFile(activity, filePath, object : FileUtils.DownloadCallback {
                override fun onProgress(progress: Int) {
//                    Log.e(TAG, "文件下载进度$progress")
                    activity.runOnUiThread {
                        mRateText.text = "$progress%"
                        var map: MutableMap<String, Any?> = mutableMapOf("progress" to progress)
                        channel?.invokeMethod("onDownload", map)
                    }
                }

                override fun onFail(msg: String) {
                    Log.e(TAG, "文件下载失败$msg")
                    activity.runOnUiThread {
                        var map: MutableMap<String, Any?> =
                            mutableMapOf("code" to 1000, "msg" to msg)
                        channel?.invokeMethod("onFail", map)
                    }
                }

                override fun onFinish(file: File) {
                    Log.e(TAG, "文件下载完成！")
                    activity.runOnUiThread {
                        openFile(file)
                    }
                }

            })
        } else {
            openFile(File(filePath))
        }
    }

    /**
     * 打开文件
     */
    private fun openFile(file: File?) {
        mContainer.removeAllViews()
        if (file != null && !TextUtils.isEmpty(file.toString())) {
            //增加下面一句解决没有TbsReaderTemp文件夹存在导致加载文件失败
            val bsReaderTemp =
                FileUtils.getDir(activity).toString() + File.separator + "TbsReaderTemp"
            val bsReaderTempFile = File(bsReaderTemp)
            if (!bsReaderTempFile.exists()) {
                val mkdir: Boolean = bsReaderTempFile.mkdir()
                if (!mkdir) {
                    Log.e(TAG, "创建$bsReaderTemp 失败")
                    var map: MutableMap<String, Any?> =
                        mutableMapOf("code" to 1001, "msg" to "TbsReaderTemp缓存文件创建失败")
                    channel?.invokeMethod("onFail", map)
                }
            }
            //文件格式
            val fileExt = FileUtils.getFileType(file.toString())
            val bool = TbsFileInterfaceImpl.canOpenFileExt(fileExt)
            Log.d(TAG, "文件是否支持$bool  文件路径：$file $bsReaderTemp $fileExt")
            if (bool) {
                val state = ActivityCompat.checkSelfPermission(activity,"android.permission.WRITE_SETTINGS")
                Log.wtf("state","$state")
                // 0 代表没有权限 1 代表有权限
                if(state == 0){
                    ActivityCompat.requestPermissions(activity, arrayOf("android.permission.WRITE_SETTINGS"), 1)
                    return
                }
                TbsFileInterfaceImpl.cleanAllFileRecord(activity);
                //加载文件
                val localBundle = Bundle()
                localBundle.putString("filePath", file.absolutePath.toString())
                localBundle.putString("tempPath", bsReaderTemp)
                localBundle.putString("fileExt", fileExt)
                localBundle.putInt("set_content_view_height", height.toInt())
                val ret = TbsFileInterfaceImpl.getInstance().openFileReader(activity, localBundle,
                    { code, args, msg ->
                        Log.e(TAG, "文件打开回调 $code  $args  $msg")
                        when(code){
                            ITbsReader.NOTIFY_CANDISPLAY->{
                                //文件即将显示
                                Log.wtf("NOTIFY_CANDISPLAY","文件即将显示")
                            }
                        }
                        if (args is Bundle) {
                            val id = args.getInt("typeId", 0)
                            if (ITbsReader.TBS_READER_TYPE_STATUS_UI_SHUTDOWN == id) {
                                //加密文档弹框取消需关闭activity
                            }
                        }
                    }, mContainer
                )
                if (ret == 0) {
                    channel?.invokeMethod("onShow", null)
                } else {
                    var map: MutableMap<String, Any?> =
                        mutableMapOf("code" to ret, "msg" to "error:$ret")
                    channel?.invokeMethod("onFail", map)
                }
            } else {
                Log.e(TAG, "文件打开失败！文件格式暂不支持")
                var map: MutableMap<String, Any?> =
                    mutableMapOf("code" to 1002, "msg" to "文件格式不支持或者打开失败")
                channel?.invokeMethod("onFail", map)
            }
        } else {
            Log.e(TAG, "文件路径无效！")
            var map: MutableMap<String, Any?> =
                mutableMapOf("code" to 1003, "msg" to "本地文件路径无效")
            channel?.invokeMethod("onFail", map)
        }
    }


    override fun dispose() {
        TbsFileInterfaceImpl.getInstance().closeFileReader()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if ("showFile" == call.method) {
            var path = call.arguments as String
            loadFile(path)
        }
    }
}
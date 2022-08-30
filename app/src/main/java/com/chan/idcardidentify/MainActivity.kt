package com.chan.idcardidentify

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chan.idcardidentify.databinding.ActivityMainBinding
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName

    private lateinit var binding: ActivityMainBinding
    private var progressDialog: ProgressDialog? = null
    private var language = "cn"

    private var ResultImage: Bitmap? = null
    private var fullImage: Bitmap? = null

    private var baseApi: TessBaseAPI? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Example of a call to a native method
//        binding.sampleText.text = stringFromJNI()
        initTess()
    }

    private fun initTess() {
        GlobalScope.launch {
            withContext(Dispatchers.Main){
                showProgress()
            }
            //init tess
            baseApi = TessBaseAPI()
            var inputStream = assets.open(language + ".traineddata")
//            val externalStorageDirectory = Environment.getExternalStorageDirectory()
            val externalStorageDirectory = getExternalFilesDir(null)
            var file = File(externalStorageDirectory?.path+"/tess/tessdata/$language.traineddata")
            var dir = File(externalStorageDirectory?.path+"/tess/tessdata")
            try {
                val exists = File(externalStorageDirectory?.path).exists()
                Log.i("asd",exists.toString())
                if (!dir.exists()){
                    dir.mkdirs()
                }
                if (!file.exists()) {
                    file.createNewFile()
                    val fos = FileOutputStream(file)
                    val buffer = ByteArray(2048)
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        fos.write(buffer, 0, len)
                    }
                    fos.close()
                    inputStream.close()

                }
            }catch (e:Exception){
                e.printStackTrace()
            }
            var isSuccess = baseApi?.init(externalStorageDirectory?.path+"/tess", language)

            withContext(Dispatchers.Main){
                if (isSuccess!=null&&isSuccess){
                    dismissProgress()
                }else{
                    Toast.makeText(this@MainActivity, "load trainedData failed", Toast.LENGTH_SHORT)
                        .show()
                    dismissProgress()
                }
            }
        }
    }

    fun search(view: View?) {
        val intent: Intent
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            intent = Intent()
            intent.action = Intent.ACTION_GET_CONTENT
        } else {
            intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
        }
        intent.type = "image/*"
        //使用选取器并自定义标题
        startActivityForResult(Intent.createChooser(intent, "选择待识别图片"), 100)
    }

    fun searchId(view: View?) {
        binding.tesstext.setText(null)
        ResultImage = null
        val bitmapResult: Bitmap? = ImageProcess.getIdNumber(fullImage, Bitmap.Config.ARGB_8888)
        fullImage?.recycle()
        ResultImage = bitmapResult
        //tesseract-ocr
        binding.idcard.setImageBitmap(bitmapResult)
    }

    fun recognition(view: View?) {
        // 识别Bitmap中的图片
        baseApi?.setImage(ResultImage)
        binding.tesstext.setText(baseApi?.getUTF8Text())
        baseApi?.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && null != data) {
            getResult(data.data)
        }
    }

    private fun getResult(uri: Uri?) {
//        safeRecycled();
        var imagePath: String? = null
        if (null != uri) {
            //在我们的魅族测试手机上发现有一个相册管家 从这里选取图片会得到类似
            //file:///storage/emulated/0/tencent/MicroMsg/WeiXin/mmexport1474966179606.jpg的uri
            if ("file" == uri.scheme) {
//                Log.i(MainActivity.TAG, "path uri 获得图片")
                imagePath = uri.path
            } else if ("content" == uri.scheme) {
//                Log.i(MainActivity.TAG, "content uri 获得图片")
                val filePathColumns = arrayOf(MediaStore.Images.Media.DATA)
                val c = contentResolver.query(uri, filePathColumns, null, null, null)
                if (null != c) {
                    if (c.moveToFirst()) {
                        val columnIndex = c.getColumnIndex(filePathColumns[0])
                        imagePath = c.getString(columnIndex)
                    }
                    c.close()
                }
            }
        }
        if (!TextUtils.isEmpty(imagePath)) {
            if (fullImage != null) {
                fullImage!!.recycle()
            }
            fullImage = toBitmap(imagePath)
            binding.tesstext.setText(null)
            binding.idcard.setImageBitmap(fullImage)
        }
    }

    fun toBitmap(pathName: String?): Bitmap? {
        if (TextUtils.isEmpty(pathName)) return null
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(pathName, o)
        var width_tmp = o.outWidth
        var height_tmp = o.outHeight
        var scale = 1
        while (true) {
            if (width_tmp <= 640 && height_tmp <= 480) {
                break
            }
            width_tmp /= 2
            height_tmp /= 2
            scale *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = scale
        opts.outHeight = height_tmp
        opts.outWidth = width_tmp
        return BitmapFactory.decodeFile(pathName, opts)
    }
    /**
     * A native method that is implemented by the 'idcardidentify' native library,
     * which is packaged with this application.
     */
//    external fun stringFromJNI(): String

//    companion object {
//        // Used to load the 'idcardidentify' library on application startup.
//        init {
//            System.loadLibrary("native-lib")
//        }
//        external fun getIdNumber(src: Bitmap?, config: Bitmap.Config?): Bitmap?
//
//    }


    private fun showProgress() {
        if (null != progressDialog) {
            progressDialog!!.show()
        } else {
            progressDialog = ProgressDialog(this)
            progressDialog!!.setMessage("请稍候...")
            progressDialog!!.isIndeterminate = true
            progressDialog!!.setCancelable(false)
            progressDialog!!.show()
        }
    }

    private fun dismissProgress() {
        if (null != progressDialog) {
            progressDialog!!.dismiss()
        }
    }
}
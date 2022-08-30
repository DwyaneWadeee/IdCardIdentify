package com.chan.idcardidentify;

import android.graphics.Bitmap;

/**
 * Created by xiang on 2017/7/23.
 */

public class ImageProcess {

    static {
        System.loadLibrary("native-lib");
    }

    public static native Bitmap getIdNumber(Bitmap src, Bitmap.Config config);
}

#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>


#define LOG_TAG "native"
#define DEFAULT_CARD_WIDTH 640
#define DEFAULT_CARD_HEIGHT 400
#define  FIX_IDCARD_SIZE Size(DEFAULT_CARD_WIDTH,DEFAULT_CARD_HEIGHT)
extern "C" {
    using namespace std;
    using namespace cv;
};

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

extern "C" extern JNIEXPORT void JNICALL Java_org_opencv_android_Utils_nBitmapToMat2
        (JNIEnv *env, jclass, jobject bitmap, jlong m_addr, jboolean needUnPremultiplyAlpha);

extern "C" extern JNIEXPORT void JNICALL Java_org_opencv_android_Utils_nMatToBitmap
        (JNIEnv *env, jclass, jlong m_addr, jobject bitmap);
extern "C" JNIEXPORT jstring JNICALL
Java_com_chan_idcardidentify_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_chan_idcardidentify_MainActivity_00024Companion_getIdNumber(JNIEnv *env, jobject type,
                                                                     jobject src, jobject config) {

}

jobject createBitmap(JNIEnv *env, Mat srcData, jobject config) {
    // Image Details
    int imgWidth = srcData.cols;
    int imgHeight = srcData.rows;
    int numPix = imgWidth * imgHeight;

    jclass bmpCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMid = env->GetStaticMethodID(bmpCls, "createBitmap",
                                                       "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject jBmpObj = env->CallStaticObjectMethod(bmpCls, createBitmapMid, imgWidth, imgHeight,
                                                  config);
    Java_org_opencv_android_Utils_nMatToBitmap(env, 0, (jlong) &srcData, jBmpObj);
//    mat2Bitmap(env, srcData, jBmpObj);
    return jBmpObj;
}


extern "C"
JNIEXPORT jobject JNICALL
Java_com_chan_idcardidentify_ImageProcess_getIdNumber(JNIEnv *env, jclass clazz, jobject src,
                                                      jobject config) {

    Mat src_img;
    Mat dst_img;

    //讲bitmap转换为Mat型格式数据
    Java_org_opencv_android_Utils_nBitmapToMat2(env, clazz, src, (jlong) &src_img, 0);


    Mat dst;
    //无损压缩//640*400
    resize(src_img, src_img,FIX_IDCARD_SIZE);

    //灰度化 变灰色
    cvtColor(src_img, dst, COLOR_BGR2GRAY);

    //二值化
    threshold(dst, dst, 100, 255, CV_THRESH_BINARY);

    //加水膨胀，发酵
    Mat erodeElement = getStructuringElement(MORPH_RECT, Size(20, 10));
    erode(dst, dst, erodeElement);

    ////轮廓检测 // arraylist
    vector< vector<Point> > contours;
    vector<Rect> rects;

    findContours(dst, contours, RETR_TREE, CHAIN_APPROX_SIMPLE, Point(0, 0));

    for (int i = 0; i < contours.size(); i++) {
        Rect rect = boundingRect(contours.at(i));
        //rectangle(dst, rect, Scalar(0, 0, 255));  // 在dst 图片上显示 rect 矩形
        if (rect.width > rect.height * 9) {
            rects.push_back(rect);
            rectangle(dst, rect, Scalar(0, 255, 255));
            dst_img = src_img(rect);
        }
    }
    if (rects.size() == 1) {
        Rect rect = rects.at(0);
        dst_img = src_img(rect);
    } else {
        int lowPoint = 0;
        Rect finalRect;
        for (int i = 0; i < rects.size(); i++) {
            Rect rect = rects.at(i);
            Point p = rect.tl();
            if (rect.tl().y > lowPoint) {
                lowPoint = rect.tl().y;
                finalRect = rect;
            }
        }
        rectangle(dst, finalRect, Scalar(255, 255, 0));
        dst_img = src_img(finalRect);

    }
    jobject bitmap = createBitmap(env, dst_img, config);


    end:
    src_img.release();
    dst_img.release();
    dst.release();

    return bitmap;


}

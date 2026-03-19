#include <jni.h>
#include <cstring>
#include "barcode_scanner.h"

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_turntable_barcodescanner_BarcodeDecoder_decodeGrayscale(
    JNIEnv* env,
    jclass /* clazz */,
    jbyteArray data,
    jint width,
    jint height)
{
    if (!data || width <= 0 || height <= 0)
        return nullptr;

    jsize len = env->GetArrayLength(data);
    if (len != width * height)
        return nullptr;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes)
        return nullptr;

    char out[512];
    int ok = barcode_decode_grayscale(
        reinterpret_cast<const unsigned char*>(bytes),
        width, height, out, sizeof(out));

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (!ok)
        return nullptr;

    return env->NewStringUTF(out);
}

} // extern "C"

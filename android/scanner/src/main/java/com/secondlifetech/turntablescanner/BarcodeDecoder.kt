package com.secondlifetech.turntablescanner

object BarcodeDecoder {
    init {
        System.loadLibrary("barcode_jni")
    }

    external fun decodeGrayscale(data: ByteArray, width: Int, height: Int): String?
}

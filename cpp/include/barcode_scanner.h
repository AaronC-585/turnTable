#ifndef BARCODE_SCANNER_H
#define BARCODE_SCANNER_H

#ifdef __cplusplus
extern "C" {
#endif

#ifdef _WIN32
  #ifdef BARCODE_SCANNER_EXPORTS
    #define BARCODE_API __declspec(dllexport)
  #else
    #define BARCODE_API __declspec(dllimport)
  #endif
#elif defined(__ANDROID__) || defined(__APPLE__)
  #define BARCODE_API __attribute__((visibility("default")))
#else
  #define BARCODE_API
#endif

/**
 * Decode 1D barcode from grayscale image buffer.
 * Buffer is row-major, one byte per pixel (0=black, 255=white).
 *
 * @param data   Pointer to grayscale bytes (must remain valid for the call)
 * @param width  Image width
 * @param height Image height
 * @param out    Output buffer for decoded text (caller allocates, e.g. 256 bytes)
 * @param out_size Size of out in bytes
 * @return 1 if a barcode was decoded, 0 otherwise. out is null-terminated on success.
 */
int BARCODE_API barcode_decode_grayscale(
  const unsigned char* data,
  int width,
  int height,
  char* out,
  int out_size
);

#ifdef __cplusplus
}
#endif

#endif /* BARCODE_SCANNER_H */

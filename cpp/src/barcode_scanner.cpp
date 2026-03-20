#include "barcode_scanner.h"
#include "ReadBarcode.h"
#include "ImageView.h"
#include <cstring>

using namespace ZXing;

int barcode_decode_grayscale(
    const unsigned char* data,
    int width,
    int height,
    char* out,
    int out_size)
{
    if (!data || !out || out_size <= 0 || width <= 0 || height <= 0)
        return 0;

    ImageView image(data, width, height, ImageFormat::Lum);
    ReaderOptions opts;
    opts.setFormats(BarcodeFormat::LinearCodes);

    auto results = ReadBarcodes(image, opts);
    if (results.empty())
        return 0;

    const auto& first = results.front();
    std::string text = first.text();
    if (text.empty())
        return 0;

    size_t len = text.size();
    if (len >= static_cast<size_t>(out_size))
        len = static_cast<size_t>(out_size) - 1;
    std::memcpy(out, text.c_str(), len);
    out[len] = '\0';
    return 1;
}

// Backwards-compat: tolerate older misspelling.
extern "C" int barcode_decode_greyscal(
    const unsigned char* data,
    int width,
    int height,
    char* out,
    int out_size)
{
    return barcode_decode_grayscale(data, width, height, out, out_size);
}

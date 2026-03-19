# 1D Barcode Scanner

Single-page scanner that shows the camera feed and decodes **1D barcodes** in real time (Code 128, EAN-13, EAN-8, UPC-A, UPC-E, Code 39, Code 93, Codabar, Interleaved 2 of 5).

## Usage

1. Open `barcode-scanner.html` in a modern browser (Chrome, Firefox, Safari, Edge).
2. Allow camera access when prompted.
3. Point the camera at a 1D barcode; the decoded value appears below the video.
4. Use **Stop scanner** / **Start scanner** to pause or resume.

**Note:** For local file opening, some browsers require HTTPS or `file://` with relaxed security. For best results, serve the file from a local server, e.g.:

```bash
# Python 3
python3 -m http.server 8080
# Then open http://localhost:8080/barcode-scanner.html
```

No build step or dependencies; Quagga2 is loaded from a CDN.

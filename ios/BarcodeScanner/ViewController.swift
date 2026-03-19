import UIKit
import AVFoundation

final class ViewController: UIViewController {

    private let previewLayer = AVCaptureVideoPreviewLayer()
    private let session = AVCaptureSession()
    private let resultLabel: UILabel = {
        let l = UILabel()
        l.translatesAutoresizingMaskIntoConstraints = false
        l.backgroundColor = UIColor(white: 0.16, alpha: 0.9)
        l.textColor = .white
        l.font = .systemFont(ofSize: 18, weight: .medium)
        l.textAlignment = .center
        l.numberOfLines = 0
        l.layer.cornerRadius = 8
        l.clipsToBounds = true
        l.text = "—"
        l.isHidden = true
        return l
    }()

    private var lastCode: String?
    private var lastCodeTime: TimeInterval = 0
    private let throttleInterval: TimeInterval = 1.5

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)

        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        view.addSubview(resultLabel)
        NSLayoutConstraint.activate([
            resultLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            resultLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            resultLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            resultLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 48)
        ])

        requestCameraAndStart()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
    }

    private func requestCameraAndStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStartSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.configureAndStartSession() }
                    else { self?.showPermissionAlert() }
                }
            }
        default:
            showPermissionAlert()
        }
    }

    private func showPermissionAlert() {
        let alert = UIAlertController(
            title: "Camera access",
            message: "Camera permission is required to scan barcodes.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in })
        present(alert, animated: true)
    }

    private func configureAndStartSession() {
        session.beginConfiguration()
        session.sessionPreset = .high

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)

        let output = AVCaptureVideoDataOutput()
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange]
        output.setSampleBufferDelegate(self, queue: DispatchQueue(label: "camera.queue"))
        if session.canAddOutput(output) { session.addOutput(output) }

        session.commitConfiguration()
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    private func processPixelBuffer(_ pixelBuffer: CVPixelBuffer) {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let base = CVPixelBufferGetBaseAddress(pixelBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)

        // BiPlanar 420: Y plane first
        guard let data = base else { return }
        let buffer = data.assumingMemoryBound(to: UInt8.self)
        var gray = [UInt8](repeating: 0, count: width * height)
        for y in 0..<height {
            for x in 0..<width {
                gray[y * width + x] = buffer[y * bytesPerRow + x]
            }
        }

        var out = [CChar](repeating: 0, count: 512)
        let ok = gray.withUnsafeBufferPointer { buf in
            barcode_decode_grayscale(
                buf.baseAddress!,
                Int32(width),
                Int32(height),
                &out,
                Int32(out.count)
            )
        }

        guard ok != 0 else { return }
        let result = String(cString: out)
        let now = CACurrentMediaTime()
        guard result != self.lastCode || (now - self.lastCodeTime) > self.throttleInterval else { return }
        self.lastCode = result
        self.lastCodeTime = now

        DispatchQueue.main.async { [weak self] in
            self?.resultLabel.text = result
            self?.resultLabel.isHidden = false
        }
    }
}

extension ViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        processPixelBuffer(pixelBuffer)
    }
}

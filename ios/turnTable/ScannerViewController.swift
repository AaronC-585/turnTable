import UIKit
import AVFoundation
import AudioToolbox

protocol ScannerDelegate: AnyObject {
    func scanner(_ scanner: ScannerViewController, didScan code: String)
}

/// Mirrors Android `MainActivity`: camera decode + toolbar actions.
final class ScannerViewController: UIViewController {

    weak var delegate: ScannerDelegate?

    private let previewLayer = AVCaptureVideoPreviewLayer()
    private let session = AVCaptureSession()
    private var device: AVCaptureDevice?
    private var torchOn = false

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
    private let searchPrefs = SearchPrefs()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Scan"

        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        view.addSubview(resultLabel)
        NSLayoutConstraint.activate([
            resultLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            resultLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            resultLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            resultLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 48),
        ])

        navigationItem.rightBarButtonItems = makeToolbarItems()
        requestCameraAndStart()
    }

    private func makeToolbarItems() -> [UIBarButtonItem] {
        let home = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(tapHome))
        let hist = UIBarButtonItem(title: "History", style: .plain, target: self, action: #selector(tapHistory))
        let set = UIBarButtonItem(title: "Settings", style: .plain, target: self, action: #selector(tapSettings))
        let flash = UIBarButtonItem(title: "Light", style: .plain, target: self, action: #selector(toggleTorch))
        let red = UIBarButtonItem(title: "Redacted", style: .plain, target: self, action: #selector(tapRedacted))
        return [home, hist, set, flash, red]
    }

    @objc private func tapHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func tapHistory() {
        navigationController?.pushViewController(SearchHistoryViewController(), animated: true)
    }

    @objc private func tapSettings() {
        navigationController?.pushViewController(SettingsViewController(), animated: true)
    }

    @objc private func tapRedacted() {
        guard let key = searchPrefs.redactedApiKey, !key.isEmpty else {
            let a = UIAlertController(title: "API key", message: "Set Redacted API key in Settings.", preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "OK", style: .default))
            present(a, animated: true)
            return
        }
        navigationController?.pushViewController(RedactedBrowseViewController(apiKey: key), animated: true)
    }

    @objc private func toggleTorch() {
        guard let d = device, d.hasTorch else { return }
        torchOn.toggle()
        try? d.lockForConfiguration()
        if torchOn {
            try? d.setTorchModeOn(level: 1.0)
        } else {
            d.torchMode = .off
        }
        d.unlockForConfiguration()
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
                    if granted { self?.configureAndStartSession() } else { self?.showPermissionAlert() }
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
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    private func configureAndStartSession() {
        session.beginConfiguration()
        session.sessionPreset = .high

        guard let dev = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: dev),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        device = dev
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
        guard result != lastCode || (now - lastCodeTime) > throttleInterval else { return }
        lastCode = result
        lastCodeTime = now

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if self.searchPrefs.beepOnScan {
                AudioServicesPlaySystemSound(1108)
            }
            self.resultLabel.text = result
            self.resultLabel.isHidden = false
            self.delegate?.scanner(self, didScan: result)
        }
    }
}

extension ScannerViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        processPixelBuffer(pixelBuffer)
    }
}

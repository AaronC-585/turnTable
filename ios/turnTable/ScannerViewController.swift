import UIKit
import AVFoundation
import AudioToolbox

protocol ScannerDelegate: AnyObject {
    func scanner(_ scanner: ScannerViewController, didScan code: String)
}

/// Mirrors Android `MainActivity`: full-screen camera; navigation actions live in overlay controls (no top bar).
final class ScannerViewController: UIViewController {

    weak var delegate: ScannerDelegate?

    private let menuButton = UIButton(type: .system)
    private let flashButton = UIButton(type: .system)

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

        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        styleChromeButton(menuButton, title: "⋯", accessibilityLabel: "Scanner menu")
        menuButton.addTarget(self, action: #selector(showScanMenu), for: .touchUpInside)
        styleChromeButton(flashButton, title: "Light", accessibilityLabel: "Flashlight")
        flashButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        flashButton.addTarget(self, action: #selector(toggleTorch), for: .touchUpInside)

        view.addSubview(menuButton)
        view.addSubview(flashButton)
        view.addSubview(resultLabel)
        NSLayoutConstraint.activate([
            menuButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            menuButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            menuButton.widthAnchor.constraint(equalToConstant: 56),
            menuButton.heightAnchor.constraint(equalToConstant: 56),

            flashButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            flashButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            flashButton.widthAnchor.constraint(equalToConstant: 56),
            flashButton.heightAnchor.constraint(equalToConstant: 56),

            resultLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            resultLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            resultLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            resultLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 48),
        ])

        updateFlashButtonAppearance()
        requestCameraAndStart()
    }

    private func styleChromeButton(_ b: UIButton, title: String, accessibilityLabel: String) {
        b.translatesAutoresizingMaskIntoConstraints = false
        b.setTitle(title, for: .normal)
        b.setTitleColor(.white, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 24, weight: .semibold)
        b.backgroundColor = UIColor(white: 0.16, alpha: 0.92)
        b.layer.cornerRadius = 28
        b.accessibilityLabel = accessibilityLabel
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(true, animated: animated)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    @objc private func showScanMenu() {
        let sheet = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Home", style: .default) { [weak self] _ in self?.tapHome() })
        sheet.addAction(UIAlertAction(title: "History", style: .default) { [weak self] _ in self?.tapHistory() })
        sheet.addAction(UIAlertAction(title: "Settings", style: .default) { [weak self] _ in self?.tapSettings() })
        sheet.addAction(UIAlertAction(title: "Redacted", style: .default) { [weak self] _ in self?.tapRedacted() })
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        if let pop = sheet.popoverPresentationController {
            pop.sourceView = menuButton
            pop.sourceRect = menuButton.bounds
        }
        present(sheet, animated: true)
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
            if #available(iOS 15.4, *) {
                try? d.setTorchModeOn(level: 1.0)
            } else {
                d.torchMode = .on
            }
        } else {
            d.torchMode = .off
        }
        d.unlockForConfiguration()
        updateFlashButtonAppearance()
    }

    private func updateFlashButtonAppearance() {
        let hasTorch = device?.hasTorch == true
        flashButton.isEnabled = hasTorch
        flashButton.alpha = hasTorch ? 1 : 0.45
        if torchOn {
            flashButton.backgroundColor = UIColor(red: 0.2, green: 0.55, blue: 0.35, alpha: 0.95)
        } else {
            flashButton.backgroundColor = UIColor(white: 0.16, alpha: 0.92)
        }
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
            DispatchQueue.main.async { [weak self] in
                self?.updateFlashButtonAppearance()
            }
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
            if self.searchPrefs.hapticOnScan {
                let h = UINotificationFeedbackGenerator()
                h.prepare()
                h.notificationOccurred(.success)
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

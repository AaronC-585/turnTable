import UIKit
import AVFoundation
import AudioToolbox

/// Companion scanner: decode → optional Redacted resolve → `turntable://search`.
final class CompanionScannerViewController: UIViewController {

    private let prefs = SearchPrefs()
    private let session = AVCaptureSession()
    private let previewLayer = AVCaptureVideoPreviewLayer()
    private var device: AVCaptureDevice?
    private var torchOn = false
    private var resolving = false
    private var lastCode: String?
    private var lastCodeTime: TimeInterval = 0
    private let throttleInterval: TimeInterval = 1.5

    private let flashButton = UIButton(type: .system)
    private let statusLabel: UILabel = {
        let l = UILabel()
        l.translatesAutoresizingMaskIntoConstraints = false
        l.backgroundColor = UIColor(white: 0.16, alpha: 0.9)
        l.textColor = .white
        l.font = .systemFont(ofSize: 16, weight: .medium)
        l.textAlignment = .center
        l.numberOfLines = 0
        l.layer.cornerRadius = 8
        l.clipsToBounds = true
        l.isHidden = true
        return l
    }()
    private let spinner: UIActivityIndicatorView = {
        let s: UIActivityIndicatorView
        if #available(iOS 13.0, *) {
            s = UIActivityIndicatorView(style: .large)
        } else {
            s = UIActivityIndicatorView(style: .whiteLarge)
        }
        s.translatesAutoresizingMaskIntoConstraints = false
        s.hidesWhenStopped = true
        s.color = .white
        return s
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Scanner"

        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        flashButton.translatesAutoresizingMaskIntoConstraints = false
        flashButton.setTitle("Light", for: .normal)
        flashButton.setTitleColor(.white, for: .normal)
        flashButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        flashButton.backgroundColor = UIColor(white: 0.16, alpha: 0.92)
        flashButton.layer.cornerRadius = 28
        flashButton.addTarget(self, action: #selector(toggleTorch), for: .touchUpInside)

        view.addSubview(flashButton)
        view.addSubview(statusLabel)
        view.addSubview(spinner)
        NSLayoutConstraint.activate([
            flashButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            flashButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            flashButton.widthAnchor.constraint(equalToConstant: 56),
            flashButton.heightAnchor.constraint(equalToConstant: 56),
            statusLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            statusLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])

        requestCameraAndStart()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(true, animated: animated)
        resolving = false
        statusLabel.isHidden = true
        spinner.stopAnimating()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
    }

    @objc private func toggleTorch() {
        guard let d = device, d.hasTorch else { return }
        torchOn.toggle()
        try? d.lockForConfiguration()
        d.torchMode = torchOn ? .on : .off
        d.unlockForConfiguration()
        flashButton.backgroundColor = torchOn
            ? UIColor(red: 0.2, green: 0.55, blue: 0.35, alpha: 0.95)
            : UIColor(white: 0.16, alpha: 0.92)
    }

    private func requestCameraAndStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStartSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.configureAndStartSession() }
                }
            }
        default:
            break
        }
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

        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            let wanted: [AVMetadataObject.ObjectType] = [
                .ean8, .ean13, .upce, .code39, .code128, .itf14, .qr,
            ]
            output.metadataObjectTypes = wanted.filter { output.availableMetadataObjectTypes.contains($0) }
        }

        session.commitConfiguration()
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    private func onBarcodeScanned(_ barcode: String) {
        if resolving { return }
        resolving = true
        if prefs.beepOnScan {
            AudioServicesPlaySystemSound(1108)
        }
        statusLabel.text = "Resolving…"
        statusLabel.isHidden = false
        spinner.startAnimating()

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            let key = self.prefs.redactedApiKey
            let hit = (key != nil && !(key ?? "").isEmpty)
                ? ScannerRedactedAssist.firstHit(apiKey: key!, barcode: barcode)
                : nil
            DispatchQueue.main.async {
                self.statusLabel.text = "Opening turnTable…"
                self.openTurnTable(barcode: barcode, artist: hit?.artist, album: hit?.album)
            }
        }
    }

    private func openTurnTable(barcode: String, artist: String?, album: String?) {
        guard let url = ScannerRedactedAssist.turnTableSearchURL(
            barcode: barcode,
            artist: artist,
            album: album
        ) else {
            resolving = false
            spinner.stopAnimating()
            statusLabel.isHidden = true
            return
        }
        UIApplication.shared.open(url) { [weak self] ok in
            guard let self = self else { return }
            if !ok {
                self.resolving = false
                self.spinner.stopAnimating()
                self.statusLabel.isHidden = true
                let a = UIAlertController(
                    title: "turnTable missing",
                    message: "Install turnTable first, then scan again.",
                    preferredStyle: .alert
                )
                a.addAction(UIAlertAction(title: "OK", style: .default))
                self.present(a, animated: true)
            }
        }
    }
}

extension CompanionScannerViewController: AVCaptureMetadataOutputObjectsDelegate {
    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !resolving,
              let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let code = obj.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines),
              !code.isEmpty
        else { return }
        let now = CACurrentMediaTime()
        guard code != lastCode || (now - lastCodeTime) > throttleInterval else { return }
        lastCode = code
        lastCodeTime = now
        onBarcodeScanned(code)
    }
}

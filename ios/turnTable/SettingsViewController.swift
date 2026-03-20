import UIKit

/// Mirrors Android `SettingsActivity` — key URL/API fields.
final class SettingsViewController: UIViewController, UIPickerViewDataSource, UIPickerViewDelegate {

    private let prefs = SearchPrefs()
    private let scroll = UIScrollView()
    private let browserPickerOptions = IosSecondaryBrowser.settingsPickerOrder
    private let browserPicker = UIPickerView()

    private let secondaryUrl = UITextView()
    private let methodField = UITextView()
    private let postBody = UITextView()
    private let postCt = UITextView()
    private let postHeaders = UITextView()
    private let redactedKey = UITextView()
    private let lastfm = UITextView()
    private let audiodb = UITextView()
    private let beepSwitch = UISwitch()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Settings"
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))

        styleField(secondaryUrl, height: 40)
        styleField(methodField, height: 40)
        styleField(postBody, height: 72)
        styleField(postCt, height: 40)
        styleField(postHeaders, height: 72)
        styleField(redactedKey, height: 40)
        styleField(lastfm, height: 40)
        styleField(audiodb, height: 40)
        redactedKey.isSecureTextEntry = true
        lastfm.isSecureTextEntry = true

        secondaryUrl.text = prefs.secondarySearchUrl
        methodField.text = prefs.method
        postBody.text = prefs.postBody
        postCt.text = prefs.postContentType
        postHeaders.text = prefs.postHeaders
        redactedKey.text = prefs.redactedApiKey
        lastfm.text = prefs.lastFmApiKey
        audiodb.text = prefs.theAudioDbApiKey
        beepSwitch.isOn = prefs.beepOnScan

        scroll.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false

        func addLabel(_ t: String) {
            let l = UILabel()
            l.textColor = UIColor(white: 0.65, alpha: 1)
            l.font = .systemFont(ofSize: 13)
            l.text = t
            stack.addArrangedSubview(l)
        }

        addLabel("Secondary search URL")
        stack.addArrangedSubview(secondaryUrl)
        addLabel("Open secondary links in")
        browserPicker.translatesAutoresizingMaskIntoConstraints = false
        browserPicker.dataSource = self
        browserPicker.delegate = self
        browserPicker.backgroundColor = UIColor(white: 0.14, alpha: 1)
        stack.addArrangedSubview(browserPicker)
        browserPicker.heightAnchor.constraint(equalToConstant: 140).isActive = true
        let currentBrowser = prefs.iosSecondaryBrowser
        if let bIdx = browserPickerOptions.firstIndex(of: currentBrowser) {
            browserPicker.selectRow(bIdx, inComponent: 0, animated: false)
        }
        addLabel("HTTP method (GET / POST)")
        stack.addArrangedSubview(methodField)
        addLabel("POST body template")
        stack.addArrangedSubview(postBody)
        addLabel("POST Content-Type")
        stack.addArrangedSubview(postCt)
        addLabel("POST headers (Key: Value per line)")
        stack.addArrangedSubview(postHeaders)
        addLabel("Redacted API key")
        stack.addArrangedSubview(redactedKey)
        addLabel("Last.fm API key")
        stack.addArrangedSubview(lastfm)
        addLabel("TheAudioDB API key")
        stack.addArrangedSubview(audiodb)
        let row = UIStackView()
        row.axis = .horizontal
        let bl = UILabel()
        bl.textColor = .white
        bl.text = "Beep on scan"
        row.addArrangedSubview(bl)
        row.addArrangedSubview(beepSwitch)
        stack.addArrangedSubview(row)

        scroll.addSubview(stack)
        view.addSubview(scroll)
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 12),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32),
        ])
    }

    private func styleField(_ t: UITextView, height: CGFloat) {
        t.translatesAutoresizingMaskIntoConstraints = false
        t.backgroundColor = UIColor(white: 0.18, alpha: 1)
        t.textColor = .white
        t.font = .systemFont(ofSize: 15)
        t.layer.cornerRadius = 6
        t.heightAnchor.constraint(equalToConstant: height).isActive = true
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }

    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        browserPickerOptions.count
    }

    func pickerView(_ pickerView: UIPickerView, attributedTitleForRow row: Int, forComponent component: Int) -> NSAttributedString? {
        NSAttributedString(
            string: browserPickerOptions[row].displayTitle,
            attributes: [.foregroundColor: UIColor.white]
        )
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        prefs.secondarySearchUrl = secondaryUrl.textTrimmed.nilIfEmpty
        prefs.method = methodField.textTrimmed.isEmpty ? SearchPrefs.Method.get : methodField.textTrimmed
        prefs.postBody = postBody.textTrimmed.nilIfEmpty
        prefs.postContentType = postCt.textTrimmed.nilIfEmpty
        prefs.postHeaders = postHeaders.textTrimmed.nilIfEmpty
        prefs.redactedApiKey = redactedKey.textTrimmed.nilIfEmpty
        let bRow = min(max(0, browserPicker.selectedRow(inComponent: 0)), max(0, browserPickerOptions.count - 1))
        let browserSel = browserPickerOptions[bRow]
        prefs.secondaryBrowserPackage = browserSel == .systemDefault ? nil : browserSel.rawValue
        prefs.lastFmApiKey = lastfm.textTrimmed.nilIfEmpty
        prefs.theAudioDbApiKey = audiodb.textTrimmed.nilIfEmpty
        prefs.beepOnScan = beepSwitch.isOn
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var nilIfEmpty: String? {
        let t = trimmed
        return t.isEmpty ? nil : t
    }
}

private extension UITextView {
    var textTrimmed: String { (text ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
}

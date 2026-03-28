import UIKit

/// App info — mirrors Android [AboutActivity].
final class AboutViewController: UIViewController {

    private static let paypalURL = URL(string: "https://paypal.me/rcbaaron?locale.x=en_US&country.x=US")!
    private static let cashAppURL = URL(string: "https://cash.app/$RCBaaron")!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "About"

        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "turnTable"
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)

        let ver = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String
        let verLine = UILabel()
        verLine.textColor = UIColor(white: 0.55, alpha: 1)
        verLine.font = .systemFont(ofSize: 14)
        if let b = build, !b.isEmpty, b != ver {
            verLine.text = "Version \(ver) (\(b))"
        } else {
            verLine.text = "Version \(ver)"
        }

        let body = UILabel()
        body.textColor = UIColor(white: 0.8, alpha: 1)
        body.font = .systemFont(ofSize: 15)
        body.numberOfLines = 0
        body.text = """
        turnTable is a native 1D barcode scanner for iOS and Android with a shared C++ (ZXing) core. It supports configurable music metadata lookups and optional Redacted tracker integration.

        Open source. See the project README and CHANGELOG in the repository for details.
        """

        let donationBody = UILabel()
        donationBody.textColor = UIColor(white: 0.8, alpha: 1)
        donationBody.font = .systemFont(ofSize: 15)
        donationBody.numberOfLines = 0
        donationBody.text = "If you find turnTable useful, you can support development with a voluntary donation via PayPal or Cash App. Thank you!"

        let paypalBtn = UIButton(type: .system)
        paypalBtn.setTitle("Donate with PayPal", for: .normal)
        paypalBtn.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        paypalBtn.backgroundColor = UIColor(red: 0, green: 0.44, blue: 0.73, alpha: 1)
        paypalBtn.setTitleColor(.white, for: .normal)
        paypalBtn.layer.cornerRadius = 10
        paypalBtn.contentEdgeInsets = UIEdgeInsets(top: 14, left: 20, bottom: 14, right: 20)
        paypalBtn.addTarget(self, action: #selector(openPayPal), for: .touchUpInside)

        let cashBtn = UIButton(type: .system)
        cashBtn.setTitle("Donate with Cash App", for: .normal)
        cashBtn.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        cashBtn.backgroundColor = UIColor(red: 0, green: 0.84, blue: 0.2, alpha: 1)
        cashBtn.setTitleColor(.white, for: .normal)
        cashBtn.layer.cornerRadius = 10
        cashBtn.contentEdgeInsets = UIEdgeInsets(top: 14, left: 20, bottom: 14, right: 20)
        cashBtn.addTarget(self, action: #selector(openCashApp), for: .touchUpInside)

        let updatesBtn = UIButton(type: .system)
        updatesBtn.setTitle("Check for updates", for: .normal)
        updatesBtn.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        updatesBtn.backgroundColor = UIColor(red: 0.35, green: 0.35, blue: 0.4, alpha: 1)
        updatesBtn.setTitleColor(.white, for: .normal)
        updatesBtn.layer.cornerRadius = 10
        updatesBtn.contentEdgeInsets = UIEdgeInsets(top: 14, left: 20, bottom: 14, right: 20)
        updatesBtn.addTarget(self, action: #selector(checkUpdates), for: .touchUpInside)

        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(verLine)
        stack.addArrangedSubview(updatesBtn)
        stack.addArrangedSubview(body)
        stack.setCustomSpacing(24, after: body)
        stack.addArrangedSubview(donationBody)
        stack.addArrangedSubview(paypalBtn)
        stack.addArrangedSubview(cashBtn)

        scroll.addSubview(stack)
        view.addSubview(scroll)
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -20),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -40),
        ])
    }

    @objc private func checkUpdates() {
        TurnTableGithubUpdate.presentCheck(from: self)
    }

    @objc private func openPayPal() {
        UIApplication.shared.open(Self.paypalURL, options: [:], completionHandler: nil)
    }

    @objc private func openCashApp() {
        UIApplication.shared.open(Self.cashAppURL, options: [:], completionHandler: nil)
    }
}

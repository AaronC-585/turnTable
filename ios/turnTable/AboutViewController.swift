import UIKit

/// App info — mirrors Android [AboutActivity].
final class AboutViewController: UIViewController {

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

        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(verLine)
        stack.addArrangedSubview(body)

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
}

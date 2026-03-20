import UIKit

/// Donation — PayPal and Cash App; mirrors Android [DonationActivity].
final class DonationViewController: UIViewController {

    private static let paypalURL = URL(string: "https://paypal.me/rcbaaron?locale.x=en_US&country.x=US")!
    private static let cashAppURL = URL(string: "https://cash.app/$RCBaaron")!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Donate"

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false

        let body = UILabel()
        body.textColor = UIColor(white: 0.8, alpha: 1)
        body.font = .systemFont(ofSize: 15)
        body.numberOfLines = 0
        body.text = "If you find turnTable useful, you can support development with a voluntary donation via PayPal or Cash App. Thank you!"

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

        stack.addArrangedSubview(body)
        stack.addArrangedSubview(paypalBtn)
        stack.addArrangedSubview(cashBtn)

        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
        ])
    }

    @objc private func openPayPal() {
        UIApplication.shared.open(Self.paypalURL, options: [:], completionHandler: nil)
    }

    @objc private func openCashApp() {
        UIApplication.shared.open(Self.cashAppURL, options: [:], completionHandler: nil)
    }
}

import UIKit

/// Mirrors Android `navigateToHome()` — pops to the root `HomeViewController`.
enum AppNavigation {
    static func navigateToHome(from viewController: UIViewController) {
        guard let nav = viewController.navigationController else { return }
        for vc in nav.viewControllers where vc is HomeViewController {
            nav.popToViewController(vc, animated: true)
            return
        }
        nav.popToRootViewController(animated: true)
    }
}

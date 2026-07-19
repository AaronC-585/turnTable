import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        let nav = UINavigationController(rootViewController: SplashViewController())
        nav.navigationBar.prefersLargeTitles = false
        window?.rootViewController = nav
        window?.makeKeyAndVisible()
        if let url = launchOptions?[.url] as? URL {
            DispatchQueue.main.async { self.handleOpenURL(url) }
        }
        return true
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        handleOpenURL(url)
        return true
    }

    private func handleOpenURL(_ url: URL) {
        guard let parsed = TurnTableSearchDeepLink.parse(url) else { return }
        guard let nav = window?.rootViewController as? UINavigationController else { return }
        let vc = SearchViewController(
            barcode: parsed.barcode,
            prefillSecondaryTerms: parsed.secondaryTerms.nilIfEmpty,
            skipRedactedPrefetch: parsed.hasResolvedRelease,
        )
        // Ensure we are past splash when possible.
        if nav.viewControllers.last is SplashViewController {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                nav.pushViewController(vc, animated: true)
            }
        } else {
            nav.pushViewController(vc, animated: true)
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

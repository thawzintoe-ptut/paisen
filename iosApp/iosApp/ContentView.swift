import ComposeApp
import SwiftUI
import UIKit

/// The whole UI is Compose. This wrapper exists only so SwiftUI has something to host.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @Environment(\.scenePhase) private var phase

    var body: some View {
        ZStack {
            ComposeView()
                // Compose pads for the status bar, home indicator and keyboard itself. Letting
                // SwiftUI inset as well would pad twice and leave the bars unpainted.
                .ignoresSafeArea()
            // #25: with App lock on, the app switcher shows a blank screen, not the ledger.
            // The key is the one UserDefaultsImportSettingsStore writes.
            if phase != .active && UserDefaults.standard.bool(forKey: "ptut-app-lock") {
                Color(uiColor: .systemBackground).ignoresSafeArea()
            }
        }
    }
}

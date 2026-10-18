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
    var body: some View {
        ComposeView()
            // Compose pads for the status bar, home indicator and keyboard itself. Letting
            // SwiftUI inset as well would pad twice and leave the bars unpainted.
            .ignoresSafeArea()
    }
}

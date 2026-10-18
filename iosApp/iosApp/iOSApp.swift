import ComposeApp
import SwiftUI

@main
struct iOSApp: App {
    // Shared-group pushes: Firebase Messaging and the notification taps live in this delegate.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var pushDelegate
    // #187: Kotlin asks this for the browser, Sign in with Apple and the Keychain.
    private let authBridge = AuthBridge()
    // #212: Quick add's mic, on-device speech only.
    private let speechBridge = SpeechBridge()

    init() {
        IosAuthBridgeKt.installAuthBridge(bridge: authBridge)
        IosSpeechBridgeKt.installSpeechBridge(bridge: speechBridge)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // FR-1: a slip sent here from the share sheet, or #190: an invite link. The Kotlin
                // side owns what happens next, so this hands over the URL and says nothing about it.
                .onOpenURL { url in
                    if url.scheme == "https" && url.path.hasPrefix("/join/") {
                        MainViewControllerKt.receiveInviteLink(url: url)
                    } else {
                        MainViewControllerKt.receiveSharedSlip(url: url)
                    }
                }
        }
    }
}

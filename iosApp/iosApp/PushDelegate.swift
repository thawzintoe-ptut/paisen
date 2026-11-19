import BackgroundTasks
import ComposeApp
import FirebaseCore
import FirebaseInstallations
import FirebaseMessaging
import UIKit
import UserNotifications

/// Shared-group pushes. Firebase Messaging lives here, in the Swift target only, so the Kotlin
/// framework gains no SDK. Kotlin decides what a token or a group id means (`IosPush.kt`).
final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate, IosInstallationBridge {
    /// Nil in a checkout without the gitignored plist: the app still runs, it just registers no token.
    private var messaging: Messaging? {
        FirebaseApp.app() == nil ? nil : Messaging.messaging()
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            IosFlagsKt.installInstallationBridge(bridge: self)
            application.registerForRemoteNotifications()
        }
        UNUserNotificationCenter.current().delegate = self
        registerSharedRefresh()
        return true
    }

    /// Until there is an APNs key, iOS wakes the app now and then to pull shared groups and say what
    /// others added (`IosSharedRefresh.kt`). It must be registered before launch finishes.
    private func registerSharedRefresh() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.sharedRefresh, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            // The next one first, so a run iOS cuts short still leaves one waiting.
            Self.scheduleSharedRefresh()
            refresh.expirationHandler = { IosSharedRefreshKt.cancelSharedRefresh() }
            IosSharedRefreshKt.runSharedRefresh { refresh.setTaskCompleted(success: true) }
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { _ in Self.scheduleSharedRefresh() }
    }

    private static let sharedRefresh = "com.ptut.expense.shared-refresh"

    /// Fifteen minutes is the soonest worth asking for; iOS picks the real time from how the phone is used.
    private static func scheduleSharedRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: sharedRefresh)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        messaging?.apnsToken = deviceToken
    }

    /// A content-available push woke the app in the background: no screen is open to sync, so Kotlin
    /// syncs here and iOS hears when it finishes. An active app already heard it through `willPresent`.
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        guard application.applicationState != .active, Self.groupId(in: userInfo) != nil else {
            completionHandler(.noData)
            return
        }
        IosSharedRefreshKt.runPushSync(uid: Self.uid(in: userInfo)) { result in
            switch result {
            case .newdata: completionHandler(.newData)
            case .failed: completionHandler(.failed)
            default: completionHandler(.noData)
            }
        }
    }

    /// FCM addresses this iPhone by its Firebase installation ID (`FirebaseMessagingInstallationIdEnabled`).
    func messaging(_ messaging: Messaging, didReceiveRegistration installationId: String?) {
        guard let installationId else { return }
        IosPushKt.receivePushToken(token: installationId)
    }

    /// No banner while the app is open: the group refreshes instead. A repeat reminder keeps the
    /// behaviour it had with no delegate, which is to show nothing in the foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if let groupId = Self.groupId(in: notification.request.content.userInfo) {
            IosPushKt.receivePushInForeground(groupId: groupId, uid: Self.uid(in: notification.request.content.userInfo))
        }
        completionHandler([])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let groupId = Self.groupId(in: response.notification.request.content.userInfo) {
            IosPushKt.receivePushTap(groupId: groupId, uid: Self.uid(in: response.notification.request.content.userInfo))
        }
        completionHandler()
    }

    /// Remote Config's fetch (`FlagRefresh.kt`) asks for this installation, its token and the plist's app id.
    func installation(completion: @escaping (String?, String?, String?) -> Void) {
        guard let app = FirebaseApp.app() else {
            completion(nil, nil, nil)
            return
        }
        let installations = Installations.installations()
        installations.installationID { id, _ in
            installations.authTokenForcingRefresh(false) { result, _ in
                completion(id, result?.authToken, app.options.googleAppID)
            }
        }
    }

    private static func groupId(in userInfo: [AnyHashable: Any]) -> String? {
        userInfo["groupId"] as? String
    }

    /// The account the push was sent to; Kotlin drops it when another account is signed in.
    private static func uid(in userInfo: [AnyHashable: Any]) -> String? {
        userInfo["uid"] as? String
    }
}

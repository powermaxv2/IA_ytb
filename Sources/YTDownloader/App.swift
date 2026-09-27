import AppKit
import SwiftUI
import UserNotifications

final class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
        // Notifications only work from a real .app bundle (not `swift run`).
        if Bundle.main.bundleIdentifier != nil {
            let center = UNUserNotificationCenter.current()
            center.delegate = self
            center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }

    func applicationWillTerminate(_ notification: Notification) {
        ProcessRegistry.shared.terminateAll()
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

@main
struct YTDownloaderApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var tools = ToolManager()
    @StateObject private var downloads = DownloadManager()

    init() {
        Prefs.register()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(tools)
                .environmentObject(downloads)
                .frame(minWidth: 760, minHeight: 640)
                .task { await tools.bootstrap() }
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 900, height: 780)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }

        Settings {
            SettingsView()
                .environmentObject(tools)
                .environmentObject(downloads)
                .preferredColorScheme(.dark)
        }
    }
}

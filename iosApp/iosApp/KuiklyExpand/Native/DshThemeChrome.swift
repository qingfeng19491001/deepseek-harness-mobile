import UIKit
import shared

@objc(DshThemeChrome)
final class DshThemeChrome: NSObject {
    @objc static let prefKey = "theme_preference"
    @objc static let isNightModeKey = "isNightMode"
    @objc static let themeDidChangedEvent = "themeDidChanged"

    @objc(systemIsDarkIn:)
    static func systemIsDark(in traits: UITraitCollection) -> Bool {
        traits.userInterfaceStyle == .dark
    }

    @objc static func systemIsDark() -> Bool {
        systemIsDark(in: UITraitCollection.current)
    }

    @objc static func resolveIsDark() -> Bool {
        resolveIsDark(systemDark: systemIsDark())
    }

    @objc(resolveIsDarkWithSystemDark:)
    static func resolveIsDark(systemDark: Bool) -> Bool {
        let raw = UserDefaults.standard.string(forKey: prefKey)
        let preference = DshThemePreference.companion.fromStorage(raw: raw)
        // AUTO 首帧先跟传入的系统深浅，避免 UITraitCollection.current 在入窗前读成 Mac 外观。
        return preference.resolvedIsDark(systemDark: systemDark, solarNight: systemDark)
    }

    @objc static func backgroundColor(_ isDark: Bool) -> UIColor {
        color(argb: isDark ? DshChromePalette.shared.DARK_BACKGROUND : DshChromePalette.shared.LIGHT_BACKGROUND)
    }

    @objc static func statusBarStyle(_ isDark: Bool) -> UIStatusBarStyle {
        isDark ? .lightContent : .darkContent
    }

    @objc(applyTo:isDark:)
    static func apply(to controller: UIViewController, isDark: Bool) {
        let bg = backgroundColor(isDark)
        controller.view.backgroundColor = bg
        controller.navigationController?.view.backgroundColor = bg
        controller.view.window?.backgroundColor = bg
        controller.setNeedsStatusBarAppearanceUpdate()
    }

    private static func color(argb: Int64) -> UIColor {
        let value = UInt32(bitPattern: Int32(truncatingIfNeeded: argb))
        return UIColor(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat((value >> 24) & 0xFF) / 255
        )
    }
}

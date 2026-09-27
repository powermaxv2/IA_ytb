import SwiftUI

enum Theme {
    static let accent = Color(red: 1.0, green: 0.27, blue: 0.38)
    static let accent2 = Color(red: 1.0, green: 0.56, blue: 0.24)
    static let gradient = LinearGradient(colors: [accent, accent2], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let stroke = Color.white.opacity(0.09)
    static let fill = Color.white.opacity(0.045)
}

struct BackgroundView: View {
    var body: some View {
        ZStack {
            Color(red: 0.055, green: 0.055, blue: 0.08)
            Circle()
                .fill(Theme.accent.opacity(0.32))
                .frame(width: 560, height: 560)
                .blur(radius: 170)
                .offset(x: -300, y: -280)
            Circle()
                .fill(Theme.accent2.opacity(0.2))
                .frame(width: 480, height: 480)
                .blur(radius: 170)
                .offset(x: 320, y: 300)
            Circle()
                .fill(Color.purple.opacity(0.16))
                .frame(width: 420, height: 420)
                .blur(radius: 160)
                .offset(x: 300, y: -320)
        }
        .ignoresSafeArea()
    }
}

struct GlassCard: ViewModifier {
    var padding: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.ultraThinMaterial))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.stroke, lineWidth: 1))
            .shadow(color: .black.opacity(0.25), radius: 20, y: 10)
    }
}

extension View {
    func glassCard(padding: CGFloat = 18) -> some View {
        modifier(GlassCard(padding: padding))
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        PrimaryButton(configuration: configuration)
    }

    private struct PrimaryButton: View {
        let configuration: ButtonStyleConfiguration
        @Environment(\.isEnabled) private var isEnabled
        @State private var hovering = false

        var body: some View {
            configuration.label
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 11)
                .background(Capsule().fill(Theme.gradient))
                .overlay(Capsule().fill(Color.white.opacity(hovering && isEnabled ? 0.1 : 0)))
                .shadow(color: Theme.accent.opacity(isEnabled ? 0.45 : 0), radius: configuration.isPressed ? 4 : 12, y: 5)
                .scaleEffect(configuration.isPressed ? 0.97 : 1)
                .opacity(isEnabled ? 1 : 0.45)
                .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
                .onHover { hovering = $0 }
        }
    }
}

struct IconButtonStyle: ButtonStyle {
    var size: CGFloat = 32
    var tint: Color = .primary

    func makeBody(configuration: ButtonStyleConfiguration) -> some View {
        IconButton(configuration: configuration, size: size, tint: tint)
    }

    private struct IconButton: View {
        let configuration: ButtonStyleConfiguration
        let size: CGFloat
        let tint: Color
        @State private var hovering = false

        var body: some View {
            configuration.label
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: size, height: size)
                .background(Circle().fill(Color.white.opacity(hovering ? 0.14 : 0.07)))
                .overlay(Circle().strokeBorder(Theme.stroke, lineWidth: 1))
                .scaleEffect(configuration.isPressed ? 0.92 : 1)
                .animation(.easeOut(duration: 0.12), value: hovering)
                .onHover { hovering = $0 }
                .contentShape(Circle())
        }
    }
}

struct GradientProgressBar: View {
    var value: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.08))
                Capsule()
                    .fill(Theme.gradient)
                    .frame(width: max(6, geo.size.width * min(1, max(0, value))))
                    .animation(.easeOut(duration: 0.35), value: value)
            }
        }
        .frame(height: 6)
    }
}

struct Thumbnail: View {
    let url: URL?
    let width: CGFloat
    let height: CGFloat
    var cornerRadius: CGFloat = 12

    var body: some View {
        AsyncImage(url: url) { phase in
            if let image = phase.image {
                image.resizable().aspectRatio(contentMode: .fill)
            } else {
                ZStack {
                    Rectangle().fill(Color.white.opacity(0.06))
                    Image(systemName: "play.rectangle")
                        .font(.system(size: height * 0.3))
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).strokeBorder(Theme.stroke, lineWidth: 1))
    }
}

/// Selectable tile used for quality and audio format choices.
struct SelectableChip: View {
    let title: String
    let badge: String
    let detail: String
    let selected: Bool
    var highlight: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text(title)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                Spacer(minLength: 4)
                if highlight {
                    Text("MAX")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Theme.gradient))
                }
                Text(badge)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(selected ? Color.white : Color.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Color.white.opacity(selected ? 0.18 : 0.08)))
            }
            Text(detail)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(selected ? Theme.accent.opacity(0.16) : Theme.fill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(selected ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Theme.stroke), lineWidth: selected ? 2 : 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

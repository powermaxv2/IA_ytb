// Draws the app icon (1024x1024 PNG). Usage: swift scripts/make_icon.swift out.png
import AppKit

let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "icon_1024.png"
let size = 1024

guard let rep = NSBitmapImageRep(
    bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
    bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
    colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0
) else { fatalError("bitmap") }

NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

// macOS icon grid: 824pt body centred in 1024 canvas.
let body = NSRect(x: 100, y: 100, width: 824, height: 824)
let shape = NSBezierPath(roundedRect: body, xRadius: 185, yRadius: 185)

let shadow = NSShadow()
shadow.shadowColor = NSColor.black.withAlphaComponent(0.35)
shadow.shadowBlurRadius = 30
shadow.shadowOffset = NSSize(width: 0, height: -12)
NSGraphicsContext.saveGraphicsState()
shadow.set()
NSColor.black.setFill()
shape.fill()
NSGraphicsContext.restoreGraphicsState()

let gradient = NSGradient(colors: [
    NSColor(red: 1.0, green: 0.56, blue: 0.24, alpha: 1),
    NSColor(red: 1.0, green: 0.27, blue: 0.38, alpha: 1),
    NSColor(red: 0.78, green: 0.12, blue: 0.42, alpha: 1),
])!
gradient.draw(in: shape, angle: -60)

// Soft top highlight
let highlight = NSGradient(colors: [NSColor.white.withAlphaComponent(0.22), NSColor.white.withAlphaComponent(0)])!
NSGraphicsContext.saveGraphicsState()
shape.addClip()
highlight.draw(in: NSRect(x: 100, y: 520, width: 824, height: 404), angle: -90)
NSGraphicsContext.restoreGraphicsState()

// Play-screen outline
let screen = NSBezierPath(roundedRect: NSRect(x: 262, y: 430, width: 500, height: 340), xRadius: 70, yRadius: 70)
screen.lineWidth = 34
NSColor.white.withAlphaComponent(0.95).setStroke()
screen.stroke()

let play = NSBezierPath()
play.move(to: NSPoint(x: 465, y: 530))
play.line(to: NSPoint(x: 465, y: 670))
play.line(to: NSPoint(x: 585, y: 600))
play.close()
NSColor.white.setFill()
play.fill()

// Download arrow + tray
let arrow = NSBezierPath()
arrow.lineWidth = 40
arrow.lineCapStyle = .round
arrow.lineJoinStyle = .round
arrow.move(to: NSPoint(x: 512, y: 400))
arrow.line(to: NSPoint(x: 512, y: 250))
arrow.move(to: NSPoint(x: 440, y: 322))
arrow.line(to: NSPoint(x: 512, y: 250))
arrow.line(to: NSPoint(x: 584, y: 322))
arrow.move(to: NSPoint(x: 350, y: 195))
arrow.line(to: NSPoint(x: 674, y: 195))
NSColor.white.setStroke()
arrow.stroke()

NSGraphicsContext.restoreGraphicsState()

guard let png = rep.representation(using: .png, properties: [:]) else { fatalError("png") }
try png.write(to: URL(fileURLWithPath: out))
print("Icon written to \(out)")

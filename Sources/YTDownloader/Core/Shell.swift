import Foundation

/// Mutable box used to hand data back from background reader threads.
final class DataBox: @unchecked Sendable {
    var data = Data()
}

/// Runs a command to completion and collects its output.
enum Shell {
    struct Result: Sendable {
        let status: Int32
        let stdout: Data
        let stderr: Data

        var stdoutString: String { String(decoding: stdout, as: UTF8.self) }
        var stderrString: String { String(decoding: stderr, as: UTF8.self) }
    }

    static func run(_ executable: URL, _ arguments: [String], environment: [String: String]? = nil) async throws -> Result {
        let process = Process()
        process.executableURL = executable
        process.arguments = arguments
        if let environment { process.environment = environment }
        let outPipe = Pipe()
        let errPipe = Pipe()
        process.standardOutput = outPipe
        process.standardError = errPipe
        process.standardInput = FileHandle.nullDevice
        try process.run()

        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let outBox = DataBox()
                let errBox = DataBox()
                let group = DispatchGroup()
                group.enter()
                DispatchQueue.global(qos: .userInitiated).async {
                    outBox.data = outPipe.fileHandleForReading.readDataToEndOfFile()
                    group.leave()
                }
                errBox.data = errPipe.fileHandleForReading.readDataToEndOfFile()
                group.wait()
                process.waitUntilExit()
                continuation.resume(returning: Result(status: process.terminationStatus, stdout: outBox.data, stderr: errBox.data))
            }
        }
    }
}

// MARK: - Streaming process (used for downloads)

enum ProcessEvent: Sendable {
    case output(String)
    case exit(Int32)
    case launchFailed(String)
}

/// Splits raw pipe data into lines (on \n and \r).
final class LineSplitter: @unchecked Sendable {
    private var buffer = Data()
    private var closed = false
    private let lock = NSLock()
    private let emit: @Sendable (String) -> Void

    init(_ emit: @escaping @Sendable (String) -> Void) {
        self.emit = emit
    }

    func append(_ data: Data) {
        lock.lock()
        buffer.append(data)
        var lines: [String] = []
        while let index = buffer.firstIndex(where: { $0 == 0x0A || $0 == 0x0D }) {
            let lineData = buffer[buffer.startIndex..<index]
            if !lineData.isEmpty {
                lines.append(String(decoding: lineData, as: UTF8.self))
            }
            buffer.removeSubrange(buffer.startIndex...index)
        }
        lock.unlock()
        lines.forEach(emit)
    }

    /// Flushes the remaining data. Returns `false` if already closed.
    func close() -> Bool {
        lock.lock()
        guard !closed else { lock.unlock(); return false }
        closed = true
        let rest = buffer
        buffer = Data()
        lock.unlock()
        if !rest.isEmpty { emit(String(decoding: rest, as: UTF8.self)) }
        return true
    }
}

/// A long-running process whose stdout/stderr lines are delivered in order as an AsyncStream.
final class StreamingProcess: @unchecked Sendable {
    private let process = Process()

    func run(executable: String, arguments: [String], environment: [String: String]) -> AsyncStream<ProcessEvent> {
        AsyncStream { continuation in
            let outPipe = Pipe()
            let errPipe = Pipe()
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = arguments
            process.environment = environment
            process.standardOutput = outPipe
            process.standardError = errPipe
            process.standardInput = FileHandle.nullDevice

            let group = DispatchGroup()
            group.enter() // stdout EOF
            group.enter() // stderr EOF
            group.enter() // termination

            let yieldLine: @Sendable (String) -> Void = { continuation.yield(.output($0)) }
            for pipe in [outPipe, errPipe] {
                let splitter = LineSplitter(yieldLine)
                pipe.fileHandleForReading.readabilityHandler = { handle in
                    let data = handle.availableData
                    if data.isEmpty {
                        handle.readabilityHandler = nil
                        if splitter.close() { group.leave() }
                    } else {
                        splitter.append(data)
                    }
                }
            }
            process.terminationHandler = { _ in group.leave() }

            do {
                try process.run()
            } catch {
                outPipe.fileHandleForReading.readabilityHandler = nil
                errPipe.fileHandleForReading.readabilityHandler = nil
                group.leave(); group.leave(); group.leave()
                continuation.yield(.launchFailed(error.localizedDescription))
                continuation.finish()
                return
            }
            ProcessRegistry.shared.add(self)

            let runningProcess = self.process
            group.notify(queue: .global()) {
                ProcessRegistry.shared.remove(self)
                continuation.yield(.exit(runningProcess.terminationStatus))
                continuation.finish()
            }
        }
    }

    func terminate() {
        if process.isRunning { process.terminate() }
    }
}

/// Keeps track of running processes so they can be stopped when the app quits.
final class ProcessRegistry: @unchecked Sendable {
    static let shared = ProcessRegistry()
    private var processes: [ObjectIdentifier: StreamingProcess] = [:]
    private let lock = NSLock()

    func add(_ p: StreamingProcess) {
        lock.lock(); processes[ObjectIdentifier(p)] = p; lock.unlock()
    }

    func remove(_ p: StreamingProcess) {
        lock.lock(); processes[ObjectIdentifier(p)] = nil; lock.unlock()
    }

    func terminateAll() {
        lock.lock(); let all = Array(processes.values); lock.unlock()
        all.forEach { $0.terminate() }
    }
}

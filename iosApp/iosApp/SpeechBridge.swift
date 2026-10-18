import AVFoundation
import ComposeApp
import Speech

/// #212: Quick add's mic. Recognition runs on the iPhone only (`requiresOnDeviceRecognition`), so
/// the audio never leaves it, and a language Apple hears only on its servers is not offered. Nothing
/// heard is kept. Kotlin decides what the words mean (`IosSpeechBridge.kt`).
final class SpeechBridge: NSObject, IosSpeechBridge {
    /// Silence after the last word that counts as the person having finished.
    private static let pause: TimeInterval = 1.5
    /// Silence before any word that counts as nothing said (#216), so "Listening…" does not wait 15 s.
    private static let firstWord: TimeInterval = 5
    /// The longest one listen runs, so a mic left on does not stay open.
    private static let longest: TimeInterval = 15

    /// Made for each listen once the session records: an engine's input node keeps the format it
    /// first saw, and one read before `.record` is active has no channels to tap.
    private var engine: AVAudioEngine?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var finish: ((String?, String?) -> Void)?
    private var heard = ""
    private var pauseTimer: Timer?
    private var limitTimer: Timer?

    func onDeviceTags(tags: [String]) -> [String] {
        tags.filter { SFSpeechRecognizer(locale: Locale(identifier: $0))?.supportsOnDeviceRecognition == true }
    }

    func listen(tag: String, completion: @escaping (String?, String?) -> Void) {
        cancel()
        finish = completion
        SFSpeechRecognizer.requestAuthorization { status in
            Self.onMain {
                guard status == .authorized else { return self.end(words: nil, error: IosSpeechBridgeKt.SPEECH_DENIED) }
                Self.askForMicrophone { granted in
                    Self.onMain {
                        guard granted else { return self.end(words: nil, error: IosSpeechBridgeKt.SPEECH_DENIED) }
                        self.start(tag: tag)
                    }
                }
            }
        }
    }

    func stop() {
        Self.onMain {
            guard let request = self.request else { return }
            self.stopAudio()
            // The task answers once more, with its final result.
            request.endAudio()
        }
    }

    func cancel() {
        Self.onMain {
            self.finish = nil
            self.task?.cancel()
            self.tearDown()
        }
    }

    private func start(tag: String) {
        // Kotlin may have cancelled while a permission dialog was up.
        guard finish != nil else { return }
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: tag)), recognizer.supportsOnDeviceRecognition else {
            return end(words: nil, error: IosSpeechBridgeKt.SPEECH_LANGUAGE_OFFLINE)
        }
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = true
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            let engine = AVAudioEngine()
            self.engine = engine
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            // Tapping a format with no channels raises an exception Swift cannot catch.
            guard format.sampleRate > 0, format.channelCount > 0 else {
                return end(words: nil, error: IosSpeechBridgeKt.SPEECH_NO_MICROPHONE)
            }
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
        } catch {
            return end(words: nil, error: IosSpeechBridgeKt.SPEECH_COULD_NOT_START)
        }
        self.request = request
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Self.onMain { self?.receive(result: result, error: error) }
        }
        limitTimer = Timer.scheduledTimer(withTimeInterval: Self.longest, repeats: false) { [weak self] _ in self?.stop() }
        pauseTimer = Timer.scheduledTimer(withTimeInterval: Self.firstWord, repeats: false) { [weak self] _ in self?.stop() }
    }

    private func receive(result: SFSpeechRecognitionResult?, error: Error?) {
        if let result {
            heard = result.bestTranscription.formattedString
            if result.isFinal { return end(words: heard, error: nil) }
            pauseTimer?.invalidate()
            pauseTimer = Timer.scheduledTimer(withTimeInterval: Self.pause, repeats: false) { [weak self] _ in self?.stop() }
        } else if error != nil {
            // A task can end on an error after words arrived; those words still count. With none it
            // is silence, which Kotlin words itself (#216), never the system's own error text.
            end(words: heard.isEmpty ? nil : heard, error: nil)
        }
    }

    private func end(words: String?, error: String?) {
        let done = finish
        finish = nil
        task?.cancel()
        tearDown()
        done?(words, error)
    }

    private func tearDown() {
        pauseTimer?.invalidate()
        pauseTimer = nil
        limitTimer?.invalidate()
        limitTimer = nil
        stopAudio()
        engine = nil
        request = nil
        task = nil
        heard = ""
        // Fails only when another app already took the session back; there is nothing to undo then.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func stopAudio() {
        guard let engine else { return }
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
    }

    private static func askForMicrophone(_ answer: @escaping (Bool) -> Void) {
        if #available(iOS 17.0, *) {
            AVAudioApplication.requestRecordPermission(completionHandler: answer)
        } else {
            AVAudioSession.sharedInstance().requestRecordPermission(answer)
        }
    }

    /// Kotlin cancels from whichever thread its coroutine was on; AVAudioEngine wants one thread.
    private static func onMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread { work() } else { DispatchQueue.main.async(execute: work) }
    }
}

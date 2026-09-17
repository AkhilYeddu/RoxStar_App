# Android Native Audio Pipeline & DSP Effect Flow

## Pipeline Specification
As specified in **Section A3** of the RoxStar Technical Assessment:
`Microphone -> Oboe input stream -> Effect processing -> Encoding / file writer -> Local Draft storage -> Playback`

```mermaid
flowchart TD
    Mic(["Microphone Hardware Input"]) -->|Analog to Digital| OboeIn["Oboe Input Stream (Exclusive, LowLatency, I16)"]

    subgraph NativeEngine ["C++ Audio Engine (JNI)"]
        OboeIn -->|onAudioReady callback| PCMBuffer["16-bit Linear PCM Audio Buffer"]

        subgraph DSPSection ["DSP Voice Effect Processing"]
            direction TB
            PCMBuffer --> SwitchEffect{Selected Effect?}
            SwitchEffect -->|ECHO| Echo["Echo Effect (Circular Buffer + Decay Feedback + Wet/Dry Mix)"]
            SwitchEffect -->|REVERB| Reverb["Reverb Effect (Schroeder/Freeverb Comb & All-Pass Network)"]
            SwitchEffect -->|NONE| Dry["Bypass / Dry Audio"]
        end

        Echo --> PostPCM["Processed 16-bit PCM Buffer (Saturation Clamped)"]
        Reverb --> PostPCM
        Dry --> PostPCM

        PostPCM --> WavWriter["WavWriter Engine"]
        WavWriter --> HeaderGen["RIFF/WAV Header Finalizer (Format chunk, Data bytecount)"]
    end

    HeaderGen -->|Save file| LocalStorage[("App Storage (/data/data/.../files/drafts/*.wav)")]

    subgraph DraftManagement ["Draft Management (Kotlin / MVVM)"]
        LocalStorage --> DraftRepo["DraftRepository (Metadata: UUID, Title, Duration, Effect)"]
        DraftRepo --> DraftListUI["Drafts List View (Display, Play, Share, Delete)"]
    end

    subgraph PlaybackPipeline ["Playback Execution"]
        DraftListUI -->|User clicks Play| OboeOut["Oboe Output Stream / AudioTrack"]
        OboeOut --> Speaker(["Speaker / Headphone Output"])
    end
```

### Key Technical Characteristics
1. **Low-Latency Streams**: Oboe is configured with `PerformanceMode::LowLatency` and `SharingMode::Exclusive` where supported by hardware.
2. **Buffer Design**: Effects utilize pre-allocated circular buffers with lock-free atomic or mutex-protected access, avoiding memory allocation in the real-time audio callback thread.
3. **Hard Clamping**: Mixed wet/dry audio is explicitly clamped between `-32768.0f` and `32767.0f` to eliminate integer overflow clipping artifacts.
4. **Clean File Finalization**: On stop, `WavWriter` seeks back to byte 4 and byte 40 to write exact chunk sizes, creating 100% compliant RIFF standard WAV files.

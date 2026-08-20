# Simple Voice Chat Standalone Client

**Standalone multi-platform client foundation for Simple Voice Chat.** This repository provides both a **Windows (.NET 8 Avalonia)** and an **Android (Kotlin/Compose)** client. The repository is intentionally security-first: it will not forge Minecraft identities, manufacture SVC session secrets, bypass whitelist or permissions, enumerate private groups, or weaken encryption.

> The project currently provides buildable Windows and Android UIs, connection-state models, safe diagnostics, audio/protocol abstractions, unit tests, and a minimal Paper integration skeleton. A fully functional SVC UDP session is **not enabled** because research of the current public API does not expose a supported, authenticated standalone bootstrap path. See [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

## Current status

| Area | Status | Notes |
|---|---|---|
| Windows Avalonia UI | Implemented | Dark UI, server fields, state, groups, input/output settings, diagnostics. |
| Android Compose UI | Implemented | Foreground audio service, saved servers, QR configuration scanner. |
| Connection states | Implemented | `Disconnected`, `Connecting`, `Authenticating`, `Connected`, `JoiningGroup`, `ConnectedToGroup`, `Disconnecting`, `Error`. |
| DNS and local UDP diagnostics | Implemented | Does not falsely claim remote UDP success on either platform. |
| SVC protocol bootstrap | Blocked safely | Requires an upstream-supported, authenticated server bootstrap. |
| UDP encryption and audio packets | Not enabled | No inferred packet implementation is shipped. |
| Opus capture/playback | Architecture prepared | Interfaces only until a verified protocol adapter exists. Android uses `AudioRecord`/`AudioTrack`. |
| Voice groups | UI prepared | No private-group bypass and no fabricated group listing. |
| Paper bridge | Buildable fail-closed skeleton | Registers via official API and starts no HTTP endpoint. |

## Why a direct IP:UDP connection is insufficient

The normal SVC client receives a server-issued secret, player UUID, effective voice host/port, MTU, codec and related session parameters through the Minecraft custom-payload path before it starts the encrypted UDP handshake. The server validates the UUID/secret pair. The current public Paper API does not provide a supported way to issue the equivalent external bootstrap for a new standalone connection; its old `getSecret(UUID)` method is deprecated and documented to return `null`.

This is an intentional security boundary, not a client-side inconvenience. The project therefore refuses to invent a secret, impersonate a player or send guessed UDP frames. Full evidence and source references are in [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

## Requirements

| Component | Requirement |
|---|---|
| Desktop client build | .NET SDK 8.0+ |
| Android client build | JDK 17, Android SDK 35, Android 10+ (API 29) target |
| Paper bridge build | JDK 21 and Maven 3.8+ |
| Runtime SVC | A server owner’s authorised Simple Voice Chat installation and a future official standalone bootstrap extension |

## Build and run the Windows client

```bash
dotnet restore StandaloneVoiceChat.sln
dotnet build StandaloneVoiceChat.sln --configuration Release --no-restore
dotnet test StandaloneVoiceChat.sln --configuration Release --no-build

dotnet publish src/StandaloneVoiceChat.UI/StandaloneVoiceChat.UI.csproj \
  --configuration Release \
  --runtime win-x64 \
  --self-contained true \
  -p:PublishSingleFile=true \
  --output artifacts/win-x64
```

To run:
```bash
dotnet run --project src/StandaloneVoiceChat.UI/StandaloneVoiceChat.UI.csproj
```

## Build the Android client

The Android client is built using Gradle and requires JDK 17.

```bash
cd android
./gradlew :protocol:test :app:assembleDebug
```

The resulting APK will be located in `android/app/build/outputs/apk/debug/`.

## Build the Paper bridge

```bash
mvn --file paper-plugin/pom.xml clean verify
```

Copy the resulting `paper-plugin/target/StandaloneVoiceBridge-*.jar` to the Paper server’s `plugins/` directory only if the server owner has approved it. It requires the `voicechat` plugin and registers through the official `BukkitVoicechatService`.

The bridge intentionally starts **no HTTP API**. Its `config.yml` is:

```yaml
enabled: true
standalone-client:
  enabled: true
api:
  enabled: false
  port: 25576
```

Changing `api.enabled` does not start a network service; this avoids exposing a credential endpoint before an upstream-approved authentication and bootstrap design exists.

## Mobile features

- **QR Code Configuration:** Android can scan a `svc://` URI to import public server coordinates (Host, Minecraft Port, Voice Port). Secrets are never encoded.
- **Foreground Audio:** The Android app includes a Foreground Service to keep the microphone active when the app is backgrounded.
- **Saved Servers:** Android retains a list of configured servers locally.

## Connection diagnostics and troubleshooting

| Message | Meaning | Recommended check |
|---|---|---|
| Address resolution failed | Hostname could not be resolved. | Check spelling, DNS and IP version. |
| Local UDP failed | The application could not create its UDP socket. | Review local firewall and endpoint security. |
| Official server bootstrap required | Expected current behaviour. | Do not alter secrets manually; consult the server owner and project protocol document. |
| Voice port inaccessible | Requires a real supported handshake to verify. | Ensure SVC UDP port and firewall mapping are correct on the server. |



## Compatibility and roadmap

The source-tree snapshot used for the research is the official Simple Voice Chat repository’s `26.2` branch. Internal packet formats are deliberately not claimed as a stable public API. Future version support belongs in independent `ISvcProtocolAdapter` implementations with integration tests per SVC release.

Before enabling real audio, the project needs a confirmed upstream-supported bootstrap extension that provides all of the following without bypassing Minecraft or SVC security:

1. An explicit server-owner policy and authenticated standalone identity.
2. Short-lived session bootstrap issuance and revocation.
3. A documented or official stable protocol hook for supported SVC versions.
4. Permission-aware group discovery/join/leave operations.
5. End-to-end interoperability tests with an ordinary Minecraft SVC player.

## Repository layout

```text
src/
  StandaloneVoiceChat.Client/
  StandaloneVoiceChat.Protocol/
  StandaloneVoiceChat.Audio/
  StandaloneVoiceChat.Network/
  StandaloneVoiceChat.ProtocolTest/
  StandaloneVoiceChat.UI/
  StandaloneVoiceChat.Tests/
android/
  app/
  protocol/
paper-plugin/
docs/
  PROTOCOL.md
  ARCHITECTURE.md
.github/workflows/build.yml
```

## License

This project is distributed under the MIT License. It contains an independent implementation foundation and does not copy Simple Voice Chat source code.

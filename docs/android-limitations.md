# Android Limitations

## Launcher role

Sidr Launcher can act as the default home app only when the user selects it as the launcher. The app must remain useful even when it is not the default launcher.

## Android version target

Minimum supported version: Android 9 (API 28).

Expect behavioral differences across OEM launchers, background restrictions, permission screens, and intent handling.

## Background execution

Android limits background work aggressively. Avoid depending on long-running background services for core launcher behavior.

Use foreground services only when justified and user-visible.

## App inventory

Installed app access can be restricted on newer Android versions. Use package visibility declarations where needed and keep app discovery scoped to launcher-relevant apps.

## Voice input

Microphone access requires runtime permission. Speech recognition availability varies by device, installed services, network state, and locale.

The launcher must provide text input fallback.

## Accessibility Service

Accessibility Service can enable advanced automation, but it is sensitive and must be optional.

Requirements:

- explicit user opt-in
- clear explanation of what it enables
- no hidden automation
- easy disable path
- core launcher must work without it

## Local AI limitations

ONNX Runtime Mobile is suitable for intent classification, NLU helpers, and embeddings. It is not the primary runtime for generative LLM responses.

Local generative LLM support should be behind an interface and enabled only when:

- model files are available
- device has sufficient memory/performance
- execution is acceptable for latency and battery
- user understands storage and performance tradeoffs

Low-end devices should use cloud AI or rule-based behavior instead.

## Network dependency

Cloud AI requires network access. Offline mode must degrade to:

- app launching
- local intent matching
- cached/simple suggestions
- clear offline messaging

## Permissions

Use least-privilege permissions. Request permissions only at the moment they are needed and explain why.

Likely permissions/features:

- internet for cloud AI
- microphone for voice input
- optional accessibility service for advanced automation
- package visibility for launcher app discovery

## OEM behavior

Some OEMs modify launchers, background policies, speech services, and settings intents. Implement fallback paths and avoid assuming every Android setting or automation target is available.

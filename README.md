# Moris Agent

Privacy-first Android personal agent for Android 10+.

## Current build

Native Kotlin + Jetpack Compose app with:

- Local agent command routing
- English / French / Kreol Morisien-friendly commands
- Android on-device speech recognition when the device supports it
- Local notes
- MUR expense tracking
- Tasks
- Per-capability permission engine: Allow / Ask / Confirm / Block
- Room/SQLite local storage
- No Android `INTERNET` permission
- No OpenAI API or cloud-AI dependency

## Privacy boundary

The manifest intentionally does **not** request `android.permission.INTERNET`. The app therefore cannot directly upload its private data to OpenAI or another web service.

Voice uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` only when Android reports an on-device recognizer is available.

## Try these commands

- `Mo finn depans 450 roupi lor lunch`
- `I spent 900 on petrol`
- `Create a note about AWS`
- `Add task finish my report`
- `Ki to kapav fer?`

## Get the APK

Every push to `main` builds an APK automatically:

**GitHub → Actions → Build Android APK → latest successful run → Artifacts → MorisAgent-debug-apk**

Download the artifact ZIP, extract `app-debug.apk`, and install it on Android.

## Updating

Push another code update to `main`; GitHub Actions builds another APK. Installing a newer APK with the same application ID/signature updates the existing app and preserves its Room database.

The current CI build uses Android's debug signing key and is intended for direct personal/testing installation. A persistent private release signing key can be added later for long-term release distribution.

## Architecture

```
User
  ↓
Voice / Text
  ↓
LocalAgentEngine
  ↓
PermissionEngine
  ↓
Approved local tools
  ↓
Room / SQLite
```

The agent never receives unrestricted Android access. New capabilities must be implemented as explicit tools and checked by the permission engine.

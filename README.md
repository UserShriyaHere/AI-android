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

Push another code update to `main`; GitHub Actions builds another APK automatically.

**Important:** Android only allows an APK to update an installed copy when both APKs use the same signing key. The current CI build uses an ephemeral debug key on GitHub-hosted runners, so this first APK is suitable for testing but is **not yet guaranteed to update in place** on later builds.

For permanent in-place updates without losing local data, configure one persistent private signing keystore in GitHub Actions secrets and use it for every release build. Do not commit a private signing key to this public repository.

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

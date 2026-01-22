# Plan: Telegram Music Streaming Integration

## Goal
Enable PixelPlayer to stream music directly from a user's Telegram account ("Saved Messages" or a personal channel) using TDLib. This allows users to treat Telegram as an unlimited cloud music locker.

## User Review Required
> [!IMPORTANT]
> **TDLib Dependency**: This requires adding the Telegram Database Library (TDLib). We will use a prebuilt generic Android library (e.g., `generated/tdlib`) or a well-known wrapper to avoid complex NDK compilation issues.
> **App Size**: This will increase the APK size by approx 10-15MB due to native libraries.

## Architecture

### 1. TDLib Integration
- **Library**: `org.drinkless.tdlib` (Standard Java/Kotlin bindings).
- **Wrapper**: Create a `TelegramClient` singleton or Hilt-scoped component to manage the JNI interaction.
- **Service**: `TelegramService` to handle background updates and file downloads.

### 2. Authentication Flow
- New Screen: `TelegramLoginScreen`.
- Steps:
  1. Input Phone Number.
  2. Input OTP (and 2FA password if enabled).
  3. On success, find/create a private channel named "PixelPlayer Cloud".

### 3. Data Storage
- **Cloud**: Audio files stored in "PixelPlayer Cloud" channel.
- **Local Cache**: Extend existing `Room` database to index these cloud songs.
  - New Entity: `CloudSong` (linking `file_id` to metadata).
  - Sync mechanism: "Pull to refresh" or background worker to scan the channel for new audio files.

### 4. Streaming Strategy (Crucial)
- **Problem**: TDLib downloads files to a temporary path, but allows partial downloads ("streaming").
- **Solution**: Implement a custom **ExoPlayer DataSource** (`TdLibDataSource`).
  - **Logic**:
    - `dataSource.open()`: Triggers `tdLib.downloadFile(file_id, priority=32)`.
    - `dataSource.read()`: Blocks and waits for `updateFile` events from TDLib, piping bytes to ExoPlayer.
    - Uses a circular buffer or PipedInputStream to bridge the async callback to synchronous read.

## Proposed Changes

### Dependencies
#### [MODIFY] [libs.versions.toml](file:///Users/stark/Documents/GitHub/PixelPlayer/gradle/libs.versions.toml)
- Add `tdlib` (prebuilt .jar/.so or external dependency).

### Data Layer
#### [NEW] [TelegramRepository.kt](file:///Users/stark/Documents/GitHub/PixelPlayer/app/src/main/java/com/theveloper/pixelplay/data/repository/TelegramRepository.kt)
- Interfaces for `sendCode`, `checkCode`, `getMe`, `getChatHistory`, `downloadFile`.

#### [NEW] [TdLibDataSource.kt](file:///Users/stark/Documents/GitHub/PixelPlayer/app/src/main/java/com/theveloper/pixelplay/data/service/player/TdLibDataSource.kt)
- The core streaming bridge.

### UI Layer
#### [NEW] [TelegramLoginScreen.kt](file:///Users/stark/Documents/GitHub/PixelPlayer/app/src/main/java/com/theveloper/pixelplay/presentation/screens/settings/TelegramLoginScreen.kt)
- UI for login.

#### [MODIFY] [NavGraph.kt](file:///Users/stark/Documents/GitHub/PixelPlayer/app/src/main/java/com/theveloper/pixelplay/presentation/navigation/NavGraph.kt)
- Add route to Login screen.

### Player Integration
#### [MODIFY] [MusicService.kt](file:///Users/stark/Documents/GitHub/PixelPlayer/app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt)
- Inject `TdLibDataSource.Factory` into ExoPlayer.

## Verification Plan

### Automated Tests
- Unit test `TdLibDataSource` (mocking TDLib callbacks).
- Unit test `TelegramRepository` authentication logic.

### Manual Verification
1. **Login**: Login with a real Telegram account. Verify "PixelPlayer Cloud" channel is created.
2. **Upload**: Upload an MP3 from the device via the app. Verify it appears in the Telegram Channel.
3. **Sync**: Verify the song appears in the App's "Cloud" list.
4. **Playback**: Click the song. Verify instant playback (streaming) without waiting for full download.
5. **Seek**: Seek to the middle of the song. Verify TDLib prioritizes that chunk and playback resumes quickly.

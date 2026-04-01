# LiteChat

A native Android chat application built with Kotlin and Jetpack Compose. Connects to the LiteChat backend API for real-time family/group messaging.

## Features

### Authentication
- Email/password sign-in with OAuth token-based authentication
- Secure credential storage using EncryptedSharedPreferences
- Auto-login on app relaunch with saved credentials
- Automatic token refresh on expiry via OkHttp Authenticator

### Conversations
- List of active conversations with last message preview and timestamps
- Unread message badges
- Start new direct chats from the user list
- Pull-to-refresh
- Real-time connection status indicator (green/red)
- Navigation drawer with sign-out option

### Chat
- Telegram-style message bubbles with sender avatars and names (group chats)
- Sliding window message loading (50 messages at a time) for efficient handling of long conversation histories
- Reply-to messages with preview
- Emoji reactions on messages via long-press picker with haptic feedback
- Clickable links (http, https, mailto, tel) in message text
- Relative timestamps in conversation list, absolute times in chat bubbles

### Attachments
- Popup menu with 4 attachment options:
  - **Take a picture** -- CameraX photo capture with front/rear camera toggle
  - **Record a video** -- CameraX video recording (HD, 30fps) with front/rear toggle and duration timer
  - **Pick from gallery** -- Multi-select images and videos via Android Photo Picker
  - **Choose a file** -- Multi-select files via system document picker
- Photo/video preview with Retake/Send confirmation before uploading
- Front camera mirroring for both photos and videos
- EXIF rotation normalization for photos
- Upload progress indicator in the input bar
- Upload error notifications via snackbar
- Inline thumbnails in chat bubbles with adaptive grid layout (1-3 per row)
- Video play icon overlay on video thumbnails
- File attachment chips with clip icon, truncated filename preserving extension, and file size

### Media Viewer
- Fullscreen image viewer with pinch-to-zoom
- Video playback with ExoPlayer (looping, controls)
- Swipeable gallery for messages with multiple attachments
- Determinate download progress for videos
- File attachments open with system app chooser

### Real-time Updates
- Long-polling for instant message delivery
- Lifecycle-aware polling (active on foreground, paused on background)
- Unread message count tracking

### Push Notifications (FCM)
- Firebase Cloud Messaging for background message delivery
- Server checks if user is active (polling) before sending FCM
- Notification tap opens the relevant conversation directly
- FCM token registered after login, unregistered on sign-out
- Automatic token refresh handling

### Localization & Theming
- English and Russian language support
- Light/dark theme following system setting
- Green color scheme with Telegram-inspired bubble colors

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room (SQLite) |
| Network | Retrofit + OkHttp + Moshi |
| Images | Coil (250MB disk cache) |
| Video | Media3 ExoPlayer |
| Camera | CameraX |
| Push Notifications | Firebase Cloud Messaging |
| Auth Storage | EncryptedSharedPreferences |
| Navigation | Navigation Compose |
| Async | Coroutines + Flow |

## Requirements

- Android SDK 26+ (Android 8.0 Oreo)
- Target SDK 35, Compile SDK 36

## Building

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```
app/src/main/java/northern/captain/litechat/app/
├── LiteChatApplication.kt          -- Hilt app, Coil image loader
├── MainActivity.kt                 -- Single activity, lifecycle-aware polling
├── config/ApiConfig.kt             -- Server URL, OAuth credentials
├── di/                             -- Hilt modules (App, Database, Network)
├── data/
│   ├── local/                      -- Room database, entities, DAOs
│   ├── remote/                     -- Retrofit APIs, auth interceptor, DTOs
│   └── repository/                 -- Auth, User, Conversation, Message, Attachment
├── domain/
│   ├── model/                      -- Domain models
│   └── polling/PollManager.kt      -- Long-polling singleton
└── ui/
    ├── theme/                      -- Material 3 theming
    ├── navigation/NavGraph.kt      -- Screen routing
    ├── auth/                       -- Sign-in screen
    ├── conversations/              -- Conversation list + user list
    ├── chat/                       -- Chat screen, bubbles, input, reactions
    ├── camera/CameraScreen.kt      -- Photo/video capture
    ├── media/                      -- Fullscreen image/video viewer
    └── components/                 -- Avatar, unread badge
```

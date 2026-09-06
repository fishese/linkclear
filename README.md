# LinkClear

Small native Android share target for Instagram, Threads and Twitter/X. Android 8+; no account, analytics, link history, or backend. This is an early debug build.

The [web cleaner](https://linkclear.fishese.cc/) accepts pasted links and lets you copy or share the result. Install it in a compatible browser to use it as a web share target. The page links to the [latest signed Android APK](https://github.com/fishese/linkclear/releases/latest/download/LinkClear.apk). Web cleaning is local and handles direct post URLs; short-link resolution requires the Android app. Unknown or unresolved web input is clearly labeled and left unchanged. The web share target uses a service worker to handle shared content without forwarding it to the server. No link content is cached or stored by the app.

## Use

1. Install the APK in `app/build/outputs/apk/debug/app-debug.apk`.
2. In Instagram, Threads or Twitter/X, choose Share → More → LinkClear.
3. Recognized direct post links open the Android share sheet with only the clean URL. Enable **Preview before opening share sheet** in LinkClear to review first.
4. Short links are resolved automatically, without an extra Clean or Resolve tap. This contacts the original site with the path token, without cookies or login. With preview off, success opens the share sheet immediately. With preview on, the Share button appears near the top of the screen. Failed lookups never forward the original token or a homepage.

You can also paste a link into the launcher screen and tap **Clean and share**. Incoming shares skip the paste editor. Recognized single post URLs are cleaned and captions are discarded. Unrecognized input (including multiple URLs) passes the original text through unchanged with a toast; preview mode shows a Share original content button. No messages are sent automatically: choose the destination in Android's share sheet.

## Formats

All examples below are synthetic.

| Input | Handling |
| --- | --- |
| `instagram.com/p/POST_ID?igsh=REMOVED` | Preserve post path, remove all query parameters and fragment |
| `instagram.com/reel/POST_ID` | Direct post |
| `instagram.com/reels/POST_ID` | Normalize to `/reel/` |
| `instagram.com/tv/POST_ID` | Preserve legacy post path |
| `instagram.com/share/p/TOKEN` | Online resolution required; also `/share/reel/` and `/share/r/` |
| `threads.com/@example/post/POST_ID?xmt=REMOVED` | Direct post; legacy `threads.net` also accepted |
| `threads.com/share/TOKEN` | Online resolution required; observed in the Android share sheet |
| `threads.com/t/POST_ID` | Online resolution required |
| `twitter.com/example/status/123456789?s=20` | Normalize to `x.com`, remove query parameters and fragment |
| `x.com/example/status/123456789?s=19&t=REMOVED` | Direct post; mobile/www hosts, `/i/web/status/`, `/i/status/` and photo/video suffixes supported |

Unknown domains, unknown paths, profiles, stories, multiple URLs, userinfo, explicit ports and malformed addresses pass through unchanged with a toast. They are never labeled as cleaned. For recognized posts, all query parameters are removed, including optional carousel or presentation selections. Known short links whose network lookup fails still show Retry rather than automatically forwarding an unresolved token. The post author's handle remains when it is part of the canonical address; this tool does not anonymize the post's author or the content itself.

Short-link lookup follows at most five hops on an exact host allowlist within the original provider. It accepts recognized direct-post redirects or canonical/og:url HTML metadata. It does not execute JavaScript or sign in. Login walls, JavaScript-only redirects, rate limits and changed formats can prevent resolution. Real-world support must be verified against current links; synthetic parser tests do not prove a post exists.

## Build and verify

Use JDK 17 and Android SDK 36. Set `sdk.dir` in an untracked `local.properties`, or configure `ANDROID_HOME`.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\test-parser.ps1
```

The app uses Android platform widgets and no runtime third-party libraries. The SDK and Gradle plugin are build dependencies. The debug APK is for development.

### Signed APK via GitHub Actions

Run **Build signed Android APK** manually from the repository's Actions tab. The workflow runs parser checks, release lint, builds the APK, verifies its signature and uploads a `LinkClear-signed-N` artifact containing `LinkClear.apk` and its SHA-256 checksum. It does not publish a GitHub Release or deploy the website.

Repository secrets required: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The keystore is decoded only into the runner's temporary directory, removed after the build, and never uploaded. Signing secrets are passed through environment variables, not command-line arguments. For local release signing, set `ANDROID_KEYSTORE_PATH` and the three password/alias variables; otherwise a local release build is unsigned.

Version 0.1.3 adds Twitter/X post cleaning and unchanged-content fallback with a toast. The 35 synthetic parser checks and local debug build/lint passed. Device regression tests cover Twitter, unchanged captions and queries, ClipData and multiple-link fallback; these require a connected device and are not executed by the hosted build workflow.

Validation on 2026-09-06: debug assembly and Android lint passed (English-only string localization warnings remain); 24 synthetic parser checks passed. Installed over wireless ADB and verified a synthetic Instagram share reached the system share sheet without its tracking query. A user-selected public Threads `/share/` link resolved to a recognized canonical post without a query using the same resolver on the desktop JVM. No live Instagram short-link lookup has been verified yet.

## Parser update path

`LinkCleaner` contains pure Java provider rules; `LinkResolver` handles network resolution; `MainActivity` owns the share flow. Adding a built-in provider currently requires rebuilding the APK. A settings-based parser editor is intentionally a later version.

For that version, replace built-in rules with a versioned declarative JSON format containing exact hosts, accepted path patterns, canonical host and resolution policy. Validate imports, show synthetic before/after tests, retain a rollback copy, and constrain network destinations independently of user rules. Do not execute downloaded JavaScript or arbitrary parser code. Add an import/export settings screen before considering automatic updates.

## Privacy

Original links are held in memory, not logged or saved. Link UI state is not persisted; screenshots and recents previews are blocked. Only the preview preference is stored. Clipboard writes happen only when Copy is tapped. Online lookup necessarily discloses the short-link path token and IP address to the original site. Documentation and test fixtures must use placeholders, never actual account handles or share tokens.

## References

- [Android: receiving shared text](https://developer.android.com/training/sharing/receive)
- [Android: sending text to the sharesheet](https://developer.android.com/training/sharing/send)
- [Meta: migration from Threads.net to Threads.com](https://about.fb.com/news/2025/04/new-features-threads-web-experience/)

Version 0.1.1 share-flow regression: seven on-device checks passed for incoming text, ClipData, reused activity delivery, visible preview sharing, homepage rejection, automatic short-link completion and short-link preview. The tests intercept outgoing choosers and use a synthetic resolver response, so no recipient app opens and no real link is transmitted. Run with `adb shell am instrument -w app.linkstripper.test/app.linkstripper.ShareFlowTest` after building and installing the Android test APK.

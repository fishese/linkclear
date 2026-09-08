# LinkClear

Small native Android share target for Instagram, Threads and Twitter/X, with editable custom site rules. Android 8+; no LinkClear account, analytics, share-link history, or backend.

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

## In-app rule builder (v0.2.0)

Open **Site rules & browser** from the app's home screen, then **Add a site from a link**:

1. Paste an example shared URL. Use **Resolve online** to follow anonymous HTTPS redirects and canonical metadata. The inspector shows whether the address changed and the visited URLs. A redirect to another host pauses until you allow that host. Alternatively, use the pasted URL without a lookup, or open the optional login browser.
2. Review the final address and edit the desired clean URL. Suggestions remove known tracking keys and fragments while keeping unfamiliar query keys that might be essential.
3. Build the suggested rule, review the exact hosts and path templates, and choose which query keys to retain. `{id}`-style placeholders match and copy one whole path segment. A single example cannot prove a site's format: review the suggestions and test a second post if possible.
4. Test before saving. **Test rule** checks the automatic path. **Test with login browser** lets you navigate to a final post and validate that URL; automatic lookup may still need browser fallback later. Changing any rule field invalidates the test.

Enabled custom rules run before built-in parsers; the first matching rule wins, and a newly saved rule goes first. Edit, disable, delete or undo your last change from the list. Built-in presets create editable overrides; removing an override restores the original parser. Hosts are exact: add separate rules for aliases such as `www` or `mobile`.

Only saved rule fields and one undo copy persist, not the sample or test URLs. Rules are declarative, with no arbitrary regular expressions, scripts, downloaded code or LLM. This version supports simple ASCII path segments, rearranging/removing entire segments, changing the output host, and keeping/removing query keys. It does not infer substring edits to opaque tokens, calculate/decode IDs, or rewrite query values. Complex login or JavaScript steps cannot be learned as an automatic redirect rule. Export/import and remote rule subscriptions are not included.

### Optional login browser and performance

The browser uses Android's installed WebView engine; no browser engine or extra runtime library is bundled. It is created only when explicitly opened in the builder or after a failed lookup. Normal shares never initialize WebView or its cookie manager. Saved rule definitions are cached; direct rules run locally, and only resolution rules make network requests.

Login cookies stay in the optional browser and can persist between visits. They are never exported to the anonymous resolver. Navigate to the post and tap **Use this URL**. **External** opens your usual browser if a site refuses embedded login; copy its final address back manually. Some providers block WebView login, so support is site-dependent. Browser data can be cleared from its own screen. Local file access, mixed HTTP content and JavaScript-to-native bridges are disabled; JavaScript and site storage are enabled for login.

On-device regression commands after installing the debug and Android test APKs:

```text
adb shell am instrument -w -e suite share app.linkstripper.test/app.linkstripper.RuleEditorTest
adb shell am instrument -w app.linkstripper.test/app.linkstripper.RuleEditorTest
```

The emulator checks cover saving/testing/generalizing a rule, overrides, disabling and undo, normal share behavior, and browser cookie persistence using synthetic pages. No real account login has been tested.

## Privacy

The share cleaner and rule builder hold example links in memory without logging them or saving their UI state. Screenshots are blocked. The preview preference, saved custom rule fields and an undo copy are stored locally. The optional browser separately retains cookies, cache and site storage until cleared; visited sites may store their own data. Clipboard writes happen only when Copy is tapped. Online lookup necessarily discloses the requested URL and IP address to the allowed site. Documentation and test fixtures must use placeholders, never actual account handles or share tokens.

## References

- [Android: receiving shared text](https://developer.android.com/training/sharing/receive)
- [Android: sending text to the sharesheet](https://developer.android.com/training/sharing/send)
- [Meta: migration from Threads.net to Threads.com](https://about.fb.com/news/2025/04/new-features-threads-web-experience/)

The regression tests intercept outgoing choosers and use synthetic resolver responses, so no recipient app opens and no real link is transmitted.

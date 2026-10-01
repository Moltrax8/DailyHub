# DailyHub

A personal productivity app — tasks, subtasks, recurring tasks, a calendar,
workout tracking and progress stats. Native Android (Kotlin + Jetpack Compose).

## First-time setup on a new machine

Only needed once. Create a file named `local.properties` in the project root
(it is git-ignored, so it never gets committed) and fill in the values below.
Each one is explained under the table.

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
RELEASE_STORE_FILE=release.jks
RELEASE_STORE_PASSWORD=<your password>
RELEASE_KEY_ALIAS=pna
RELEASE_KEY_PASSWORD=<your password>
```

| Value | Required? | Where to get it |
|-------|-----------|-----------------|
| `sdk.dir` | ✅ Yes | Path to your Android SDK |
| `RELEASE_STORE_FILE` | For release builds | Your signing keystore |
| `RELEASE_STORE_PASSWORD` | For release builds | Password you set when creating the keystore |
| `RELEASE_KEY_ALIAS` | For release builds | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | For release builds | Password for that key |

### `sdk.dir` — your Android SDK path

The folder where the Android SDK is installed. If you have Android Studio, it's
usually:

```
C:\Users\<you>\AppData\Local\Android\Sdk
```

You can confirm the exact path in Android Studio under
**Settings → Languages & Frameworks → Android SDK** (the "Android SDK Location"
field at the top). Escape the colon and backslashes as shown in the example
above (`C\:\\Users\\...`).

### Exercise demo videos — per-user key, no setup needed to build

The app builds and runs with no API key. Exercise demo videos are powered by
each user's **own** [ExerciseDB (RapidAPI)](https://rapidapi.com/justin-WFnsXH_t6/api/exercisedb)
key, entered in the app under **Settings → Exercise Demo Videos** (free tier
is enough). No key = videos stay off; everything else works.

### `RELEASE_*` — signing keystore (only for release builds)

A release APK must be signed. You create the keystore once and reuse it forever
(the **same** file is required to update an already-installed app).

Create it with `keytool` (bundled with the JDK), run from the project root:

```cmd
keytool -genkeypair -v -keystore release.jks -alias pna -keyalg RSA -keysize 2048 -validity 10000
```

It will ask you to set a password and some name/org details. Then fill
`local.properties`:

- `RELEASE_STORE_FILE` → `release.jks` (the file you just created, in the project root)
- `RELEASE_STORE_PASSWORD` → the password you typed
- `RELEASE_KEY_ALIAS` → `pna` (the `-alias` from the command above)
- `RELEASE_KEY_PASSWORD` → the key password (same as the store password unless you set a different one)

> ⚠️ Back up `release.jks` and its passwords. Without them you can't update an
> already-installed app.

## Build a release APK (Windows)

Open **cmd** in the project folder and run:

```cmd
gradlew.bat :app:assembleRelease
```

The signed APK lands here:

```
app\build\outputs\apk\release\app-release.apk
```

Copy it to your phone and open it to install (enable "install from unknown
sources" if Android asks).

## Build a debug APK (Windows)

Quicker to build and needs **no keystore** — Gradle auto-signs it. Good for a
friend who just wants to try the app without doing the signing setup above.

```cmd
gradlew.bat :app:assembleDebug
```

The APK lands here:

```
app\build\outputs\apk\debug\app-debug.apk
```

Install it the same way (copy to phone, open, allow unknown sources).

## Debug vs release

- **Debug** — no signing setup, but **not optimized**: bigger APK, no code
  shrinking, slightly slower. Meant for quick testing.
- **Release** — optimized: code shrinking / obfuscation (R8) is on, so the APK
  is smaller and faster. This is the version to actually keep and update.

> They are signed with different keys, so Android treats them as separate apps —
> one can't update over the other. Pick one and stick with it.

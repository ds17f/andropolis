# Releasing Micropolis

This document tells you how to publish the app on Google Play and on F-Droid.
Part 1 is a one-time setup. Part 2 is the procedure for each release.

The application ID is `io.github.ds17f.micropolis`. You cannot change it after
the first publication.

## Part 1: One-time setup

### 1.1 Get permission to use the name

"Micropolis" is a registered trademark of Micropolis GmbH. The Micropolis Public
Name License permits the name for non-commercial use with attribution. The
license does not permit the name in marketing material without written
permission. A store listing can be marketing material.

1. Send a request to Micropolis GmbH (<https://www.micropolis.com>). Ask for
   permission to use the name "Micropolis" in the Google Play and F-Droid
   listings for this free, open-source port.
2. Keep the answer with the project records.
3. If you do not get permission, change the name in these places before the
   first publication:
   - `android/app/src/main/AndroidManifest.xml` (`android:label`)
   - `fastlane/metadata/android/en-US/title.txt`
   - `fdroid/io.github.ds17f.micropolis.yml` (`AutoName`)

Do not use a name that is similar to "SimCity". Electronic Arts owns that
trademark.

### 1.2 Make the upload key

Google Play signs the published app with its own key (Play App Signing). You
sign each upload with an upload key.

1. Make the upload keystore:

   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Keep `upload.jks` and its passwords in a safe place outside the repository.

CAUTION: DO NOT PUT THE KEYSTORE OR ITS PASSWORDS IN GIT. IF YOU LOSE THE UPLOAD
KEY, YOU MUST ASK GOOGLE TO RESET IT.

### 1.3 Add the GitHub secrets

Go to the repository on GitHub, then Settings > Secrets and variables > Actions.
Add these secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The output of `base64 -w0 upload.jks` |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | The key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | The JSON key of the Play service account (see 1.4) |

If the keystore secrets are not there, the release workflow makes an unsigned
APK. If `PLAY_SERVICE_ACCOUNT_JSON` is not there, the workflow does not upload
to Google Play.

### 1.4 Set up Google Play

1. In the Play Console, make a new app. Use the package name
   `io.github.ds17f.micropolis`.
2. Fill in the store listing. Use the text and the images in
   `fastlane/metadata/android/en-US/`.
3. Fill in the content rating, the data safety form, and the target audience.
   The app collects no data and has no network access.
4. Upload the first AAB manually. Google Play needs one manual upload before
   the API can upload.
5. In Google Cloud, make a service account. Give it access to this app in the
   Play Console (Users and permissions). Make a JSON key for the service
   account. Put the JSON in the `PLAY_SERVICE_ACCOUNT_JSON` secret.

### 1.5 Exact alarms and Google Play policy

The app uses exact alarms for background-play events. Google Play permits
`USE_EXACT_ALARM` only for alarm-clock and calendar apps. Thus the app uses
`SCHEDULE_EXACT_ALARM`. The user gives this permission in Settings >
Background > Exact timing. Without the permission, the app uses inexact alarms.
Then a notification can be some minutes late.

In the Play Console, declare the exact-alarm use if the console asks for it.

## Part 2: Each release

1. Make sure that the working tree is clean and that CI is green on `main`.
2. Run the release script with the new version and a short changelog:

   ```
   scripts/release.sh 0.2.0 "Add the Tokyo scenario fixes."
   ```

   The script increases `versionCode` by one. It sets `versionName`, writes the
   changelog file, makes a commit, and makes the tag `v0.2.0`.
3. Push the commit and the tag:

   ```
   git push origin main --tags
   ```

4. Look at the "Release" workflow on GitHub Actions. The workflow does these
   steps:
   - It runs the engine tests.
   - It builds the signed APK and the AAB.
   - It makes a GitHub Release with the APK.
   - It uploads the AAB to the Play internal testing track.
5. In the Play Console, promote the release from internal testing to
   production when you are ready.

## Part 3: F-Droid

F-Droid builds the app from the source code at each tag. F-Droid signs the APK
with its own key. Thus the F-Droid app and the Play app cannot update each
other.

### 3.1 First submission

1. Make a release tag (Part 2). F-Droid needs a tag to build.
2. Fork <https://gitlab.com/fdroid/fdroiddata>.
3. Copy `fdroid/io.github.ds17f.micropolis.yml` to
   `metadata/io.github.ds17f.micropolis.yml` in your fork.
4. Update `versionName`, `versionCode`, `commit`, `CurrentVersion`, and
   `CurrentVersionCode` to the values of the tag.
5. Run `fdroid lint` and `fdroid build -v -l io.github.ds17f.micropolis` if you
   have the F-Droid tools. If you do not have them, the CI of the merge request
   does these checks.
6. Open a merge request. Answer the questions of the reviewers.

### 3.2 Known risks for the F-Droid build

- The build uses NDK `30.0.16248370`. The F-Droid build server can have only
  older NDK versions. If the build fails for this reason, change `ndkVersion`
  in `android/app/build.gradle.kts` to a version that F-Droid has. Then make a
  new tag.
- The source includes the `MicropolisCore` submodule. The metadata sets
  `submodules: true`.
- The license is GPL-3.0-or-later with the additional terms of Electronic Arts
  (section 7). See `LICENSE` and `LICENSE-MICROPOLIS.md`.

### 3.3 Later releases

After the first merge, F-Droid finds new tags automatically
(`UpdateCheckMode: Tags`). You do not have to do more steps.

## Checklist before the first publication

- [ ] Permission from Micropolis GmbH for the name, or a new name in all three
      places (1.1).
- [ ] Upload keystore made and kept safe (1.2).
- [ ] GitHub secrets added (1.3).
- [ ] Play Console app made, first AAB uploaded manually, service account added
      (1.4).
- [ ] CI is green on `main`.
- [ ] First tag pushed with `scripts/release.sh` (Part 2).
- [ ] F-Droid merge request opened (3.1).

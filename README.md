# Monica Steam

[简体中文](README_ZH.md) | **English**

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Monica-Steam?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Monica-Steam/total?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/releases)
[![Last commit](https://img.shields.io/github/last-commit/JoyinJoester/Monica-Steam?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/commits/main)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

> **Status:** Public test build. Monica Steam is still under active development and is not a stable or official Steam client.

Monica Steam is a Steam-focused Android client derived from the Steam surfaces in Monica Android. It brings Steam Guard, account management, mobile confirmations, library, store, friends, chat, notifications, and Steam account backup into a standalone application.

## Relationship with Monica Pass

[Monica Pass](https://github.com/Monica-Pass/Monica) is the main Monica ecosystem and local-first password-vault project. Its [Monica Android client](https://github.com/Monica-Pass/Monica/tree/main/Monica%20for%20Android) contains both password-management features and the original Steam experience.

Monica Steam was extracted from that Steam experience and is developed as a separate product:

| Project | Role | Link |
| --- | --- | --- |
| Monica Pass | Local-first password vault and broader Monica ecosystem | [GitHub repository](https://github.com/Monica-Pass/Monica) · [Website](https://monica-pass.github.io/Monica/) |
| Monica Android | Full Monica Android client and source of the original Steam module | [Android project](https://github.com/Monica-Pass/Monica/tree/main/Monica%20for%20Android) |
| Monica Steam | Standalone Steam-focused Android client | [This repository](https://github.com/JoyinJoester/Monica-Steam) |

- Monica Steam uses its own application ID, local sandbox, release cycle, and repository: `takagi.ru.monica.steamapp`.
- It may reuse Monica's Material 3 design, navigation, security, storage, and Steam components where appropriate, but it does not modify Monica Android.
- It does **not** include the Monica Pass password vault, Bitwarden, KeePass, autofill, or password-management workflows.
- Monica Steam is not a replacement for Monica Pass and cannot open or manage Monica Pass password-vault records.
- `maFile`, Steam account ZIP backups, MDBX support, and WebDAV in this app are for Steam account data only; they should not be confused with Monica Pass vault synchronization.

The extraction baseline and source relationship are documented in [`SOURCE.md`](./SOURCE.md).

## Features

### Steam accounts and Steam Guard

- Steam Guard time-based codes and multiple Steam accounts.
- `maFile`, key-only, credential, and QR-code imports.
- Login approvals, mobile confirmations, and authorized-device management.
- Authenticator removal and Steam account switching.
- Local encrypted account storage with optional MDBX-backed storage.

### Library and game data

- Steam library, family-sharing entries, play time, achievements, and ownership details.
- Account-level game count, play time, and estimated value summaries.
- Recent-play-time filters, completion filters, distribution charts, and play-activity heatmaps.
- Cached library data for offline viewing, with synchronization when the network is available.

### Steam Store

- Store browsing, search, regional prices, currency conversion, and account-region filtering.
- Purchase options, editions, DLC, bundles, system requirements, screenshots, and player reviews.
- Native cart and wishlist views with final checkout handled by Steam's official flow.
- Store events and points-store content where Steam exposes compatible data.

### Friends, chat, and notifications

- Friends list, friend profiles, unified direct and group conversations, and group management.
- Text, Steam emoji, stickers, image messages, message copy, reactions, reports, and chat search where supported.
- Steam notifications, unread state, gift and confirmation-related actions, and notification details.

### Appearance and backup

- Monica color schemes, including Monica Plus palettes.
- Material 3 Expressive layouts, floating Dock navigation, Dock ordering, and interface scaling.
- Steam-only `maFile` backup and restore through WebDAV, plus ZIP export/import.
- Main-password and biometric protection, log viewing, log cleanup, and file sharing.
- Account and recently-played-game widgets.

## Data and security boundaries

- The app ID is `takagi.ru.monica.steamapp`; its databases and preferences are isolated by Android's application sandbox.
- Monica Steam and Monica Android can be installed side by side, but data is not automatically shared between them.
- Back up existing `maFile` files before importing, migrating, or enabling remote backup.
- Steam pages and mobile APIs can change without notice. Store prices, gifts, notifications, and chat actions may depend on account region, Steam session state, or endpoint availability.
- Never treat a test build as the only copy of your Steam authenticator or account data.

## Development

### Prerequisites

- Android Studio stable channel.
- JDK 17 or newer.
- Android SDK 35 and an Android 8.0+ device for the current application configuration.

### Useful commands

Run JVM tests without creating an APK or AAB:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Build development or release variants when a package is explicitly needed:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release signing is supplied externally through `keystore.properties` or the `MONICA_STEAM_RELEASE_*` environment variables. Never commit signing files or credentials.

## Repository guide

- [`README_ZH.md`](./README_ZH.md) — Chinese project overview.
- [`RELEASE_NOTES.md`](./RELEASE_NOTES.md) — first public test release and known limitations.
- [`SOURCE.md`](./SOURCE.md) — extraction baseline and relationship to Monica Android.
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) — adapted components and their licenses.

## Third-party and official-service notice

Monica Steam is an unofficial third-party client. It is not affiliated with, endorsed by, or sponsored by Valve Corporation. Steam, Steam Guard, and related marks belong to their respective owners.

Some interactions use Steam web pages or non-public mobile endpoints. When an operation involves purchases, gifts, account security, or final confirmation, the official Steam result remains authoritative.

## Support

For bug reports and feature requests, use the [Monica Steam issue tracker](https://github.com/JoyinJoester/Monica-Steam/issues). If Monica projects are useful to you, development support is available through [爱发电](https://afdian.com/a/JoyinJoester) and [Ko-fi](https://ko-fi.com/joyinjoester).

## License

Copyright (c) 2025 JoyinJoester.

Monica Steam is released under the [GNU General Public License v3.0](LICENSE). See [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) for additional attribution and license information.

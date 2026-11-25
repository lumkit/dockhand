# Releasing Dockhand

## Automated release

Pushing an annotated tag starts the release workflow:

```bash
git tag -a v0.1.0-beta.1 -m "Dockhand 0.1.0-beta.1"
git push origin v0.1.0-beta.1
```

The workflow:

1. Checks that `dockhand.version` equals the tag without the leading `v`
2. Builds the macOS DMG on a GitHub-hosted macOS runner
3. Imports the optional Apple signing certificate
4. Signs the app and DMG when a certificate is configured
5. Generates a SHA-256 checksum
6. Generates release notes from commits since the previous tag
7. Creates a GitHub Release and uploads the DMG and checksum
8. Marks tags containing `-` as pre-releases

The workflow definition is [.github/workflows/release.yml](../.github/workflows/release.yml).

## Before tagging

Update the version in `gradle.properties`:

```properties
dockhand.version=0.1.0-beta.1
```

Commit it, then create the tag with exactly the same version.

## Signing certificate setup

Signing is optional. Without the secrets below, the workflow publishes an unsigned/ad-hoc DMG.

Add these repository secrets in Settings → Secrets and variables → Actions:

| Secret | Description |
|---|---|
| `MAC_CERTIFICATE_P12` | Base64-encoded `.p12` export of the signing certificate and private key |
| `MAC_CERTIFICATE_PASSWORD` | Password used when exporting the `.p12` |
| `MAC_KEYCHAIN_PASSWORD` | Any temporary password for the CI keychain |

Export the certificate from Keychain Access, then encode it:

```bash
base64 -i certificate.p12 | pbcopy
```

Paste the result into `MAC_CERTIFICATE_P12`.

The workflow prints the signing identity hash, not the certificate or password.

## Local signed build

For a local build using the certificate hash from `local.properties`:

```bash
./gradlew :desktopApp:packageSignedDmg
```

## Limitations

- The current certificate is Apple Development, not Developer ID Application
- The generated build is not notarized
- Gatekeeper may show a warning for downloaded builds
- Developer ID Application and notarization can be added later without changing the release workflow structure

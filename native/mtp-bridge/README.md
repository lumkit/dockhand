# dockhand MTP native bridge

This crate is the JVM ↔ Rust bridge for MTP.

- MTP implementation: [mtp-rs](https://github.com/vdavid/mtp-rs) (MIT OR Apache-2.0)
- The bridge is built as a `cdylib`: `libdockhand_mtp.dylib`
- The JVM side loads it with the JDK 25 FFM API (`java.lang.foreign`)
- Gradle runs `cargo build --release` through `shared:buildMtpBridge`

## Requirements

- Rust 1.90+
- macOS (the project currently targets macOS arm64)

## Build

```sh
cargo build --release
```

The output is:

```
target/release/libdockhand_mtp.dylib
```

## C ABI

- `dockhand_mtp_storages()`
- `dockhand_mtp_list(device_key, storage_id, path)`
- `dockhand_mtp_mkdir(device_key, storage_id, path)`
- `dockhand_mtp_upload(device_key, storage_id, local_path, remote_path)`
- `dockhand_mtp_download(device_key, storage_id, remote_path, local_path)`
- `dockhand_mtp_free(pointer)`

`device_key` is the USB location ID reported by `dockhand_mtp_storages`;
multiple connected devices are enumerated independently.

All string-returning functions return a JSON payload:

```json
{"ok": true, "data": ...}
```

or:

```json
{"ok": false, "error": "..."}
```

The caller must pass the returned pointer to `dockhand_mtp_free`.

## Development override

Set `DOCKHAND_MTP_BRIDGE` to a compiled dylib path to bypass resource extraction.

## Third-party notices

`mtp-rs` is licensed under MIT OR Apache-2.0.
See `THIRD_PARTY_NOTICES.md`.
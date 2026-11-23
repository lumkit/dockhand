use mtp_rs::mtp::{ByteRange, MtpDevice, NewObjectInfo, ObjectHandle, Storage, StorageId};
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};
use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::sync::{Mutex, OnceLock};
use tokio::io::AsyncWriteExt;
use tokio_util::io::ReaderStream;

static OP_LOCK: Mutex<()> = Mutex::new(());

fn active_lists() -> &'static Mutex<HashMap<u64, mtp_rs::CancelToken>> {
    static ACTIVE: OnceLock<Mutex<HashMap<u64, mtp_rs::CancelToken>>> = OnceLock::new();
    ACTIVE.get_or_init(|| Mutex::new(HashMap::new()))
}

fn cancelled_list_requests() -> &'static Mutex<HashSet<u64>> {
    static CANCELLED: OnceLock<Mutex<HashSet<u64>>> = OnceLock::new();
    CANCELLED.get_or_init(|| Mutex::new(HashSet::new()))
}

fn runtime() -> &'static tokio::runtime::Runtime {
    static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("failed to create MTP runtime")
    })
}

fn to_c_string(value: String) -> *mut c_char {
    match CString::new(value) {
        Ok(cstr) => cstr.into_raw(),
        Err(_) => CString::new(r#"{"ok":false,"error":"result contains NUL"}"#)
            .expect("static string")
            .into_raw(),
    }
}

fn error_message(error: &mtp_rs::Error) -> String {
    if error.is_exclusive_access() {
        format!("MTP 设备被其他程序占用（例如 OpenMTP / Android File Transfer）：{error}")
    } else if error.is_disconnected() {
        format!("MTP 设备已断开：{error}")
    } else {
        error.to_string()
    }
}

fn run_operation<F>(future: F) -> *mut c_char
where
    F: std::future::Future<Output = Result<Value, String>>,
{
    let _guard = OP_LOCK.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    let result = catch_unwind(AssertUnwindSafe(|| runtime().block_on(future)));
    let value = match result {
        Ok(Ok(data)) => json!({ "ok": true, "data": data }),
        Ok(Err(error)) => json!({ "ok": false, "error": error }),
        Err(_) => json!({ "ok": false, "error": "MTP native bridge panicked" }),
    };
    to_c_string(value.to_string())
}

fn read_c_string(pointer: *const c_char) -> Result<String, String> {
    if pointer.is_null() {
        return Err("null string".to_string());
    }
    let value = unsafe { CStr::from_ptr(pointer) };
    value
        .to_str()
        .map(ToOwned::to_owned)
        .map_err(|error| format!("invalid UTF-8: {error}"))
}

async fn open_storage(
    device_key: u64,
    storage_id: u64,
    include_metadata: bool,
) -> Result<(MtpDevice, Storage), String> {
    // GetObjectPropList 很快，但 Android 往往不返回 DateModified。
    // 列表需要时间信息时走逐对象 GetObjectInfo；其他操作用快速路径。
    let device = MtpDevice::builder()
        .use_object_prop_list(!include_metadata)
        .open_by_location(device_key)
        .await
        .map_err(|error| error_message(&error))?;
    let storage = device
        .storage(StorageId(storage_id))
        .await
        .map_err(|error| error_message(&error))?;
    Ok((device, storage))
}

async fn resolve_folder(
    storage: &Storage,
    path: &str,
) -> Result<Option<ObjectHandle>, String> {
    let mut parent: Option<ObjectHandle> = None;
    for segment in path.trim_matches('/').split('/').filter(|part| !part.is_empty()) {
        let objects = storage
            .list_objects(parent)
            .await
            .map_err(|error| error_message(&error))?;
        let found = objects
            .into_iter()
            .find(|object| object.is_folder() && object.filename == segment)
            .ok_or_else(|| format!("路径不存在：{path}"))?;
        parent = Some(found.handle);
    }
    Ok(parent)
}

fn split_parent(path: &str) -> Result<(String, String), String> {
    let trimmed = path.trim_matches('/');
    if trimmed.is_empty() {
        return Err("路径不能为空".to_string());
    }
    let (parent, name) = match trimmed.rfind('/') {
        Some(index) => (format!("/{}", &trimmed[..index]), &trimmed[index + 1..]),
        None => ("/".to_string(), trimmed),
    };
    if name.is_empty() {
        return Err("名称不能为空".to_string());
    }
    Ok((parent, name.to_string()))
}

fn modified_local(info: &mtp_rs::mtp::ObjectInfo) -> Option<String> {
    info.modified.map(|date| {
        format!(
            "{:04}-{:02}-{:02}T{:02}:{:02}:{:02}",
            date.year, date.month, date.day, date.hour, date.minute, date.second
        )
    })
}

async fn device_and_storages() -> Result<Value, String> {
    let devices = MtpDevice::list_devices().map_err(|error| error_message(&error))?;
    let mut device_values = Vec::new();

    for device in devices {
        let device_key = device.location_id;
        let opened = match MtpDevice::open_by_location(device_key).await {
            Ok(opened) => opened,
            Err(error) => {
                device_values.push(json!({
                    "key": device_key,
                    "manufacturer": device.manufacturer.clone().unwrap_or_default(),
                    "model": device.product.clone().unwrap_or_default(),
                    "serial": device.serial_number.clone().unwrap_or_default(),
                    "storages": [],
                    "error": error_message(&error),
                }));
                continue;
            }
        };

        let device_info = opened.device_info();
        let storages = match opened.storages().await {
            Ok(storages) => storages,
            Err(error) => {
                device_values.push(json!({
                    "key": device_key,
                    "manufacturer": device_info.manufacturer.clone(),
                    "model": device_info.model.clone(),
                    "serial": device_info.serial_number.clone(),
                    "storages": [],
                    "error": error_message(&error),
                }));
                continue;
            }
        };

        let storage_values = storages
            .iter()
            .map(|storage| {
                let info = storage.info();
                json!({
                    "id": storage.id().0,
                    "description": info.description,
                    "freeSpace": info.free_space,
                    "totalCapacity": info.total_capacity,
                    "writable": info.is_writable,
                })
            })
            .collect::<Vec<_>>();

        device_values.push(json!({
            "key": device_key,
            "manufacturer": device_info.manufacturer.clone(),
            "model": device_info.model.clone(),
            "serial": device_info.serial_number.clone(),
            "storages": storage_values,
        }));
    }

    Ok(json!({ "devices": device_values }))
}

async fn list_entries(
    request_id: u64,
    device_key: u64,
    storage_id: u64,
    path: String,
    include_metadata: bool,
) -> Result<Value, String> {
    let (_device, storage) = open_storage(device_key, storage_id, include_metadata).await?;
    let parent = resolve_folder(&storage, &path).await?;

    let token = mtp_rs::CancelToken::new();
    active_lists()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .insert(request_id, token.clone());
    if cancelled_list_requests()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .remove(&request_id)
    {
        token.cancel();
    }

    let result = storage.list_objects_with_cancel(parent, Some(&token)).await;

    active_lists()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .remove(&request_id);
    cancelled_list_requests()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .remove(&request_id);

    let objects = result.map_err(|error| {
        if matches!(error, mtp_rs::Error::Cancelled) {
            "MTP 列表已取消".to_string()
        } else {
            error_message(&error)
        }
    })?;
    let entries = objects
        .into_iter()
        .filter(|object| !object.filename.starts_with('.'))
        .map(|object| {
            json!({
                "handle": object.handle.0,
                "storageId": object.storage_id.0,
                "name": object.filename,
                "isDirectory": object.is_folder(),
                "size": object.size,
                "modifiedLocal": modified_local(&object),
            })
        })
        .collect::<Vec<_>>();
    Ok(Value::Array(entries))
}

async fn create_folder(device_key: u64, storage_id: u64, path: String) -> Result<Value, String> {
    let (_device, storage) = open_storage(device_key, storage_id, false).await?;
    let (parent_path, name) = split_parent(&path)?;
    let parent = resolve_folder(&storage, &parent_path).await?;
    let handle = storage
        .create_folder(parent, &name)
        .await
        .map_err(|error| error_message(&error))?;
    Ok(json!({ "handle": handle.0 }))
}

async fn upload_file(device_key: u64, storage_id: u64, local_path: String, remote_path: String) -> Result<Value, String> {
    let local = Path::new(&local_path);
    let size = tokio::fs::metadata(local)
        .await
        .map_err(|error| format!("无法读取本地文件：{error}"))?
        .len();
    let file = tokio::fs::File::open(local)
        .await
        .map_err(|error| format!("无法打开本地文件：{error}"))?;
    let stream = ReaderStream::new(file);

    let (_device, storage) = open_storage(device_key, storage_id, false).await?;
    let (parent_path, name) = split_parent(&remote_path)?;
    let parent = resolve_folder(&storage, &parent_path).await?;
    let handle = storage
        .upload(parent, NewObjectInfo::file(name, size), stream)
        .await
        .map_err(|error| error.to_string())?;
    Ok(json!({ "handle": handle.0, "size": size }))
}

async fn download_file(
    device_key: u64,
    storage_id: u64,
    remote_path: String,
    local_path: String,
) -> Result<Value, String> {
    let (_device, storage) = open_storage(device_key, storage_id, false).await?;
    let (parent_path, name) = split_parent(&remote_path)?;
    let parent = resolve_folder(&storage, &parent_path).await?;
    let objects = storage
        .list_objects(parent)
        .await
        .map_err(|error| error_message(&error))?;
    let object = objects
        .into_iter()
        .find(|item| item.filename == name && !item.is_folder())
        .ok_or_else(|| format!("文件不存在：{remote_path}"))?;

    let mut download = storage
        .download(object.handle, ByteRange::Full)
        .await
        .map_err(|error| error_message(&error))?;
    let mut file = tokio::fs::File::create(&local_path)
        .await
        .map_err(|error| format!("无法创建本地文件：{error}"))?;
    while let Some(chunk) = download.next_chunk().await {
        let bytes = chunk.map_err(|error| error_message(&error))?;
        file.write_all(&bytes)
            .await
            .map_err(|error| format!("写入本地文件失败：{error}"))?;
    }
    file.flush()
        .await
        .map_err(|error| format!("写入本地文件失败：{error}"))?;
    Ok(json!({ "bytes": object.size }))
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_storages() -> *mut c_char {
    run_operation(device_and_storages())
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_list(
    request_id: u64,
    device_key: u64,
    storage_id: u64,
    path: *const c_char,
) -> *mut c_char {
    let path = read_c_string(path);
    run_operation(async move {
        let path = path?;
        list_entries(request_id, device_key, storage_id, path, true).await
    })
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_list_fast(
    request_id: u64,
    device_key: u64,
    storage_id: u64,
    path: *const c_char,
) -> *mut c_char {
    let path = read_c_string(path);
    run_operation(async move {
        let path = path?;
        list_entries(request_id, device_key, storage_id, path, false).await
    })
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_cancel_list(request_id: u64) {
    let token = active_lists()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .get(&request_id)
        .cloned();
    if let Some(token) = token {
        token.cancel();
    } else {
        cancelled_list_requests()
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .insert(request_id);
    }
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_mkdir(
    device_key: u64,
    storage_id: u64,
    path: *const c_char,
) -> *mut c_char {
    let path = read_c_string(path);
    run_operation(async move {
        let path = path?;
        create_folder(device_key, storage_id, path).await
    })
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_upload(
    device_key: u64,
    storage_id: u64,
    local_path: *const c_char,
    remote_path: *const c_char,
) -> *mut c_char {
    let local_path = read_c_string(local_path);
    let remote_path = read_c_string(remote_path);
    run_operation(async move {
        upload_file(device_key, storage_id, local_path?, remote_path?).await
    })
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_download(
    device_key: u64,
    storage_id: u64,
    remote_path: *const c_char,
    local_path: *const c_char,
) -> *mut c_char {
    let remote_path = read_c_string(remote_path);
    let local_path = read_c_string(local_path);
    run_operation(async move {
        download_file(device_key, storage_id, remote_path?, local_path?).await
    })
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_free(pointer: *mut c_char) {
    if !pointer.is_null() {
        unsafe {
            drop(CString::from_raw(pointer));
        }
    }
}

#[no_mangle]
pub extern "C" fn dockhand_mtp_version() -> *mut c_char {
    to_c_string(json!({ "ok": true, "data": { "version": env!("CARGO_PKG_VERSION") } }).to_string())
}
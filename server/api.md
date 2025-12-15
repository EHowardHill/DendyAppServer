This document describes the REST API for the Minimalist App Store backend.

**Base URL:** (e.g., `http://192.168.1.X:5000`)

-----

### 1\. List All Apps

Retrieves the catalog of all applications currently hosted on the store.

  * **Endpoint:** `GET /api/list`
  * **Response:** `200 OK` (JSON Array)
    ```json
    [
      {
        "name": "Dendy Launcher",
        "package_name": "com.cinemint.dendy",
        "version": "1.0.2",
        "version_code": 12,
        "download_url": "/download/com.cinemint.dendy",
        "icon_url": "/icon/com.cinemint.dendy"
      },
      {
        "name": "TV Picker",
        "package_name": "com.cinemint.tvpicker",
        "version": "1.0",
        "version_code": 4,
        "download_url": "/download/com.cinemint.tvpicker",
        "icon_url": "/icon/com.cinemint.tvpicker"
      }
    ]
    ```

### 2\. Check for Updates

Checks which installed apps have a newer version available on the server.

  * **Endpoint:** `POST /api/updates`
  * **Content-Type:** `application/json`
  * **Request Body:** A JSON object mapping installed package names to their current integer version codes.
    ```json
    {
      "com.cinemint.dendy": 10,
      "com.cinemint.tvpicker": 4,
      "com.google.youtube": 9999
    }
    ```
  * **Response:** `200 OK` (JSON Array)
    Returns a list of *only* the apps that require an update.
    ```json
    [
      {
        "package_name": "com.cinemint.dendy",
        "name": "Dendy Launcher",
        "current_version": 10,
        "new_version": 12,
        "version_name": "1.0.2",
        "download_url": "/download/com.cinemint.dendy",
        "icon_url": "/icon/com.cinemint.dendy"
      }
    ]
    ```
    *Note: In this example, `TV Picker` is ignored because version 4 matches the server, and `YouTube` is ignored because it doesn't exist on the server.*

### 3\. Download APK

Downloads the actual Android package file.

  * **Endpoint:** `GET /download/<package_name>`
  * **Example:** `GET /download/com.cinemint.dendy`
  * **Response:** Binary file stream (`application/vnd.android.package-archive`). The filename will be formatted as `<package_name>.apk`.

### 4\. Fetch Icon

Downloads the app icon as a standardized PNG.

  * **Endpoint:** `GET /icon/<package_name>`
  * **Example:** `GET /icon/com.cinemint.dendy`
  * **Response:** Binary image stream (`image/png`).

-----

### Technical Notes for the Frontend Developer

1.  **Version Codes:** The update logic relies strictly on integer comparison. The client must retrieve its own `PackageInfo.versionCode` to send in the `/api/updates` request.
2.  **Concurrency:** The `download_url` provides a direct stream. For large APKs, the client should handle this asynchronously (e.g., using `DownloadManager` or Coroutines) to avoid blocking the UI.
3.  **Icons:** All icons are pre-converted to PNG by the server. No vector or adaptive icon handling is required on the client side.
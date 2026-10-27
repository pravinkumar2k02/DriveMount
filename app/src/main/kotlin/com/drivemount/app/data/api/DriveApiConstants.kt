package com.drivemount.app.data.api

/**
 * Constants for the Google Drive REST API v3.
 *
 * Contains base URLs, OAuth scopes, field selectors, pagination defaults,
 * upload configuration, and Google Workspace MIME type mappings.
 */
object DriveApiConstants {

    // ── API URLs ─────────────────────────────────────────────────────────

    /** Base URL for the Google Drive API v3 metadata endpoints. */
    const val BASE_URL = "https://www.googleapis.com/drive/v3/"

    /** Base URL for the Google Drive API v3 media-upload endpoint. */
    const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

    /** Google OAuth 2.0 token exchange endpoint. */
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    // ── OAuth Scopes ─────────────────────────────────────────────────────

    /** Full read/write access to all of a user's Google Drive files. */
    const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"

    /** Access to the user's email address. */
    const val SCOPE_EMAIL = "email"

    /** Access to the user's basic profile information. */
    const val SCOPE_PROFILE = "profile"

    // ── Field Selectors ──────────────────────────────────────────────────

    /** Partial-response field mask for a single [DriveFile] resource. */
    const val FIELDS_FILE =
        "id,name,mimeType,size,modifiedTime,parents,trashed,thumbnailLink," +
            "webContentLink,webViewLink,capabilities,exportLinks"

    /** Partial-response field mask for a file-list response including pagination. */
    val FIELDS_LIST = "files($FIELDS_FILE),nextPageToken,incompleteSearch"

    // ── Pagination & Upload ──────────────────────────────────────────────

    /** Maximum number of files returned per page in a list request. */
    const val PAGE_SIZE = 200

    /** Chunk size in bytes for resumable uploads (8 MB). */
    const val UPLOAD_CHUNK_SIZE = 8L * 1024L * 1024L

    // ── Google Workspace MIME Types ──────────────────────────────────────

    /** MIME type for Google Docs documents. */
    const val MIME_GOOGLE_DOCUMENT = "application/vnd.google-apps.document"

    /** MIME type for Google Sheets spreadsheets. */
    const val MIME_GOOGLE_SPREADSHEET = "application/vnd.google-apps.spreadsheet"

    /** MIME type for Google Slides presentations. */
    const val MIME_GOOGLE_PRESENTATION = "application/vnd.google-apps.presentation"

    /** MIME type for Google Drawings. */
    const val MIME_GOOGLE_DRAWING = "application/vnd.google-apps.drawing"

    /** MIME type for Google Forms. */
    const val MIME_GOOGLE_FORM = "application/vnd.google-apps.form"

    /** MIME type for Google Apps Script projects. */
    const val MIME_GOOGLE_SCRIPT = "application/vnd.google-apps.script"

    /** MIME type for Google Sites. */
    const val MIME_GOOGLE_SITE = "application/vnd.google-apps.site"

    /** MIME type for Google Jamboard. */
    const val MIME_GOOGLE_JAM = "application/vnd.google-apps.jam"

    /** MIME type for Google Maps. */
    const val MIME_GOOGLE_MAP = "application/vnd.google-apps.map"

    /** MIME type for Google Drive folders. */
    const val MIME_GOOGLE_FOLDER = "application/vnd.google-apps.folder"

    /** MIME type for Google Drive shortcuts. */
    const val MIME_GOOGLE_SHORTCUT = "application/vnd.google-apps.shortcut"

    // ── Export MIME Types ─────────────────────────────────────────────────

    /** Export MIME type for Microsoft Word documents. */
    const val MIME_DOCX =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** Export MIME type for Microsoft Excel spreadsheets. */
    const val MIME_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Export MIME type for Microsoft PowerPoint presentations. */
    const val MIME_PPTX =
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    /** Export MIME type for PDF files. */
    const val MIME_PDF = "application/pdf"

    /** Export MIME type for PNG images. */
    const val MIME_PNG = "image/png"

    /** Export MIME type for SVG images. */
    const val MIME_SVG = "image/svg+xml"

    /** Export MIME type for JSON (Apps Script). */
    const val MIME_JSON = "application/vnd.google-apps.script+json"

    /**
     * Maps each Google Workspace MIME type to the preferred export MIME type
     * used when downloading a copy of the file.
     */
    val EXPORT_MIME_TYPE_MAP: Map<String, String> = mapOf(
        MIME_GOOGLE_DOCUMENT to MIME_DOCX,
        MIME_GOOGLE_SPREADSHEET to MIME_XLSX,
        MIME_GOOGLE_PRESENTATION to MIME_PPTX,
        MIME_GOOGLE_DRAWING to MIME_PNG,
        MIME_GOOGLE_SCRIPT to MIME_JSON,
        MIME_GOOGLE_FORM to MIME_PDF,
        MIME_GOOGLE_SITE to MIME_PDF,
        MIME_GOOGLE_JAM to MIME_PDF,
        MIME_GOOGLE_MAP to MIME_PDF,
    )

    /**
     * Custom HTTP header used to pass the account identifier through OkHttp
     * interceptors. The [com.drivemount.app.auth.AuthInterceptor] reads and
     * removes this header before the request is sent over the network.
     */
    const val HEADER_ACCOUNT_ID = "X-Account-Id"
}

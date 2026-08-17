package com.ast.ft2clone;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public final class Ft2DocumentsProvider extends DocumentsProvider {
    private static final String ROOT_ID = "ft2-workspace";
    private static final String DOCUMENT_ROOT_ID = "root";

    private static final String[] DEFAULT_ROOT_PROJECTION = {
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_MIME_TYPES,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_SUMMARY,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_ICON
    };

    private File workspace;

    @Override
    public boolean onCreate() {
        try {
            workspace = Workspace.getDirectory(providerContext());
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DOCUMENT_ROOT_ID);
        row.add(DocumentsContract.Root.COLUMN_TITLE, providerContext().getString(R.string.workspace_title));
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, providerContext().getString(R.string.workspace_summary));
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher);
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                | DocumentsContract.Root.FLAG_LOCAL_ONLY
                | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, workspace.getUsableSpace());
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, resolveDocument(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
        throws FileNotFoundException {
        File parent = resolveDocument(parentDocumentId);
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        File[] children = parent.listFiles();
        if (children != null) {
            Arrays.sort(children, Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File child : children) {
                includeFile(result, child);
            }
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
        throws FileNotFoundException {
        File file = resolveDocument(documentId);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
        throws FileNotFoundException {
        File parent = resolveDocument(parentDocumentId);
        File created = Workspace.uniqueFile(parent, displayName);
        try {
            boolean success = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)
                ? created.mkdir()
                : created.createNewFile();
            if (!success) {
                throw new IOException("create returned false");
            }
            notifyChanged(parent);
            return documentIdForFile(created);
        } catch (IOException exception) {
            throw fileNotFound("Could not create " + displayName, exception);
        }
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = resolveDocument(documentId);
        if (file.equals(workspace)) {
            throw new FileNotFoundException("The workspace root cannot be deleted");
        }
        File parent = file.getParentFile();
        if (!deleteRecursively(file)) {
            throw new FileNotFoundException("Could not delete " + file.getName());
        }
        notifyChanged(parent);
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File source = resolveDocument(documentId);
        if (source.equals(workspace)) {
            throw new FileNotFoundException("The workspace root cannot be renamed");
        }
        File destination = Workspace.uniqueFile(source.getParentFile(), displayName);
        if (!source.renameTo(destination)) {
            throw new FileNotFoundException("Could not rename " + source.getName());
        }
        notifyChanged(destination.getParentFile());
        return documentIdForFile(destination);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            File parent = resolveDocument(parentDocumentId);
            File child = resolveDocument(documentId);
            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        return mimeTypeForFile(resolveDocument(documentId));
    }

    private void includeFile(MatrixCursor result, File file) throws FileNotFoundException {
        String documentId = documentIdForFile(file);
        String mimeType = mimeTypeForFile(file);
        int flags;
        if (file.isDirectory()) {
            flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
        } else {
            flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                | DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                | DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
        }

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            file.equals(workspace) ? providerContext().getString(R.string.workspace_title) : file.getName());
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType);
        row.add(DocumentsContract.Document.COLUMN_FLAGS, file.equals(workspace)
            ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            : flags);
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.isFile() ? file.length() : null);
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(DocumentsContract.Document.COLUMN_ICON, file.equals(workspace) ? R.mipmap.ic_launcher : null);
    }

    private File resolveDocument(String documentId) throws FileNotFoundException {
        try {
            File file;
            if (DOCUMENT_ROOT_ID.equals(documentId)) {
                file = workspace;
            } else if (documentId.startsWith(DOCUMENT_ROOT_ID + "/")) {
                file = new File(workspace, documentId.substring(DOCUMENT_ROOT_ID.length() + 1));
            } else {
                throw new FileNotFoundException("Unknown document ID");
            }

            File canonical = file.getCanonicalFile();
            String rootPath = workspace.getCanonicalPath();
            String filePath = canonical.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("Document escaped workspace");
            }
            if (!canonical.exists()) {
                throw new FileNotFoundException("Document does not exist");
            }
            return canonical;
        } catch (IOException exception) {
            throw fileNotFound("Could not resolve document", exception);
        }
    }

    private String documentIdForFile(File file) throws FileNotFoundException {
        try {
            String rootPath = workspace.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.equals(rootPath)) {
                return DOCUMENT_ROOT_ID;
            }
            if (!filePath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("File is outside workspace");
            }
            return DOCUMENT_ROOT_ID + "/" + filePath.substring(rootPath.length() + 1);
        } catch (IOException exception) {
            throw fileNotFound("Could not create document ID", exception);
        }
    }

    private String mimeTypeForFile(File file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        }

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        switch (extension) {
            case "xm": return "audio/x-xm";
            case "mod": return "audio/x-mod";
            case "s3m": return "audio/x-s3m";
            case "stm": return "audio/x-stm";
            case "wav": return "audio/wav";
            case "aif":
            case "aiff": return "audio/aiff";
            case "flac": return "audio/flac";
            case "ogg": return "audio/ogg";
            case "mp3": return "audio/mpeg";
            default:
                String detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                return detected != null ? detected : "application/octet-stream";
        }
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return false;
            }
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private void notifyChanged(File file) {
        if (file != null && getContext() != null) {
            getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri("com.ast.ft2clone.documents", documentIdUnchecked(file)),
                null
            );
        }
    }

    private String documentIdUnchecked(File file) {
        try {
            return documentIdForFile(file);
        } catch (FileNotFoundException exception) {
            return DOCUMENT_ROOT_ID;
        }
    }

    private android.content.Context providerContext() {
        if (getContext() == null) {
            throw new IllegalStateException("DocumentsProvider has no context");
        }
        return getContext();
    }

    private static FileNotFoundException fileNotFound(String message, Exception cause) {
        FileNotFoundException result = new FileNotFoundException(message);
        result.initCause(cause);
        return result;
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }
}

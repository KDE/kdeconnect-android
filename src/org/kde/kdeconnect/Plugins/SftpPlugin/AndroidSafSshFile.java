/*
 * Copyright 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.apache.sshd.common.file.SshFile;
import org.kde.kdeconnect.Helpers.FilesHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TargetApi(21)
public class AndroidSafSshFile implements SshFile {
    private static final String TAG = AndroidSafSshFile.class.getSimpleName();

    private final String virtualFileName;
    private DocumentInfo documentInfo;
    private Uri parentUri;
    private final AndroidSafFileSystemView fileSystemView;

    AndroidSafSshFile(final AndroidSafFileSystemView fileSystemView, Uri parentUri, Uri uri, String virtualFileName) {
        this.fileSystemView = fileSystemView;
        this.parentUri = parentUri;
        this.documentInfo = new DocumentInfo(fileSystemView.context, uri);
        this.virtualFileName = virtualFileName;
    }

    @Override
    public String getAbsolutePath() {
        return virtualFileName;
    }

    @Override
    public String getName() {
        /* From NativeSshFile, looks a lot like new File(virtualFileName).getName() to me */

        // strip the last '/'
        String shortName = virtualFileName;
        int filelen = virtualFileName.length();
        if (shortName.charAt(filelen - 1) == File.separatorChar) {
            shortName = shortName.substring(0, filelen - 1);
        }

        // return from the last '/'
        int slashIndex = shortName.lastIndexOf(File.separatorChar);
        if (slashIndex != -1) {
            shortName = shortName.substring(slashIndex + 1);
        }

        return shortName;
    }

    @Override
    public String getOwner() {
        return fileSystemView.userName;
    }

    @Override
    public boolean isDirectory() {
        return documentInfo.isDirectory;
    }

    @Override
    public boolean isFile() {
        return documentInfo.isFile;
    }

    @Override
    public boolean doesExist() {
        return documentInfo.exists;
    }

    @Override
    public long getSize() {
        return documentInfo.length;
    }

    @Override
    public long getLastModified() {
        return documentInfo.lastModified;
    }

    @Override
    public boolean setLastModified(long time) {
        //TODO
        /* Throws UnsupportedOperationException on API 26
        try {
            ContentValues updateValues = new ContentValues();
            updateValues.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, time);
            result = fileSystemView.context.getContentResolver().update(documentInfo.uri, updateValues, null, null) != 0;
            documentInfo.lastModified = time;
        } catch (NullPointerException ignored) {}
        */
        return true;
    }

    @Override
    public boolean isReadable() {
        return documentInfo.canRead;
    }

    @Override
    public boolean isWritable() {
        return documentInfo.canWrite;
    }

    @Override
    public boolean isExecutable() {
        return documentInfo.isDirectory;
    }

    @Override
    public boolean isRemovable() {
        Log.d(TAG, "isRemovable() - is this ever called?");

        return false;
    }

    public SshFile getParentFile() {
        Log.d(TAG,"getParentFile() - is this ever called");

        return null;
    }

    @Override
    public boolean delete() {
        boolean ret;

        try {
            ret = DocumentsContract.deleteDocument(fileSystemView.context.getContentResolver(), documentInfo.uri);
        } catch (FileNotFoundException e) {
            ret = false;
        }

        return ret;
    }

    @Override
    public boolean create() {
        return create(parentUri, FilesHelper.getMimeTypeFromFile(virtualFileName), getName());
    }

    private boolean create(Uri parentUri, String mimeType, String name) {
        Uri uri = null;
        try {
            uri = DocumentsContract.createDocument(fileSystemView.context.getContentResolver(), parentUri, mimeType, name);

            if (uri != null) {
                documentInfo = new DocumentInfo(fileSystemView.context, uri);
                if (!name.equals(documentInfo.displayName)) {
                    delete();
                    return false;
                }
            }
        } catch (FileNotFoundException ignored) {}

        return uri != null;
    }

    @Override
    public void truncate() {
        if (documentInfo.length > 0) {
            delete();
            create();
        }
    }

    @Override
    public boolean move(final SshFile dest) {
        boolean success = false;

        Uri destParentUri = ((AndroidSafSshFile)dest).parentUri;

        if (destParentUri.equals(parentUri)) {
            //Rename
            try {
                Uri newUri = DocumentsContract.renameDocument(fileSystemView.context.getContentResolver(), documentInfo.uri, dest.getName());
                if (newUri != null) {
                    success = true;
                    documentInfo.uri = newUri;
                }
            } catch (FileNotFoundException ignored) {}
        } else {
            // Move:
            String sourceTreeDocumentId = DocumentsContract.getTreeDocumentId(parentUri);
            String destTreeDocumentId = DocumentsContract.getTreeDocumentId(((AndroidSafSshFile) dest).parentUri);

            if (sourceTreeDocumentId.equals(destTreeDocumentId) && Build.VERSION.SDK_INT >= 24) {
                try {
                    Uri newUri = DocumentsContract.moveDocument(fileSystemView.context.getContentResolver(), documentInfo.uri, parentUri, destParentUri);
                    if (newUri != null) {
                        success = true;
                        parentUri = destParentUri;
                        documentInfo.uri = newUri;
                    }
                } catch (Exception e) {
                    Log.e(TAG,"DocumentsContract.moveDocument() threw an exception", e);
                }
            } else {
                try {
                    if (dest.create()) {
                        try (InputStream in = createInputStream(0); OutputStream out = dest.createOutputStream(0)) {
                            byte[] buffer = new byte[10 * 1024];
                            int read;

                            while ((read = in.read(buffer)) > 0) {
                                out.write(buffer, 0, read);
                            }

                            out.flush();

                            delete();
                            success = true;
                        } catch (IOException e) {
                            if (dest.doesExist()) {
                                dest.delete();
                            }
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        return success;
    }

    @Override
    public boolean mkdir() {
        return create(parentUri, DocumentsContract.Document.MIME_TYPE_DIR, getName());
    }

    @Override
    public List<SshFile> listSshFiles() {
        if (!documentInfo.isDirectory) {
            return null;
        }

        final ContentResolver resolver = fileSystemView.context.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(documentInfo.uri, DocumentsContract.getDocumentId(documentInfo.uri));
        final ArrayList<AndroidSafSshFile> results = new ArrayList<>();

        Cursor c = resolver.query(childrenUri, new String[]
                { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null);

        while (c != null && c.moveToNext()) {
            final String documentId = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
            final String displayName = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            final Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(documentInfo.uri, documentId);
            results.add(new AndroidSafSshFile(fileSystemView, parentUri, documentUri, virtualFileName + File.separatorChar + displayName));
        }

        if (c != null) {
            c.close();
        }

        return Collections.unmodifiableList(results);
    }

    @Override
    public OutputStream createOutputStream(final long offset) throws IOException {
        return fileSystemView.context.getContentResolver().openOutputStream(documentInfo.uri);
    }

    @Override
    public InputStream createInputStream(final long offset) throws IOException {
        return fileSystemView.context.getContentResolver().openInputStream(documentInfo.uri);
    }

    @Override
    public void handleClose() {
        // Nop
    }

    @Override
    public Map<Attribute, Object> getAttributes(boolean followLinks) {
        Map<SshFile.Attribute, Object> attributes = new HashMap<>();
        for (SshFile.Attribute attr : SshFile.Attribute.values()) {
            switch (attr) {
                case Uid:
                case Gid:
                case NLink:
                    continue;
            }
            attributes.put(attr, getAttribute(attr, followLinks));
        }

        return attributes;
    }

    @Override
    public Object getAttribute(Attribute attribute, boolean followLinks) {
        Object ret;

        switch (attribute) {
            case Size:
                ret = documentInfo.length;
                break;
            case Uid:
                ret = 1;
                break;
            case Owner:
                ret = getOwner();
                break;
            case Gid:
                ret = 1;
                break;
            case Group:
                ret = getOwner();
                break;
            case IsDirectory:
                ret = documentInfo.isDirectory;
                break;
            case IsRegularFile:
                ret = documentInfo.isFile;
                break;
            case IsSymbolicLink:
                ret = false;
                break;
            case Permissions:
                Set<Permission> tmp = new HashSet<>();
                if (documentInfo.canRead) {
                    tmp.add(SshFile.Permission.UserRead);
                    tmp.add(SshFile.Permission.GroupRead);
                    tmp.add(SshFile.Permission.OthersRead);
                }
                if (documentInfo.canWrite) {
                    tmp.add(SshFile.Permission.UserWrite);
                    tmp.add(SshFile.Permission.GroupWrite);
                    tmp.add(SshFile.Permission.OthersWrite);
                }
                if (isExecutable()) {
                    tmp.add(SshFile.Permission.UserExecute);
                    tmp.add(SshFile.Permission.GroupExecute);
                    tmp.add(SshFile.Permission.OthersExecute);
                }
                ret = tmp.isEmpty()
                        ? EnumSet.noneOf(SshFile.Permission.class)
                        : EnumSet.copyOf(tmp);
                break;
            case CreationTime:
                ret = documentInfo.lastModified;
                break;
            case LastModifiedTime:
                ret = documentInfo.lastModified;
                break;
            case LastAccessTime:
                ret = documentInfo.lastModified;
                break;
            case NLink:
                ret = 0;
                break;
            default:
                ret =  null;
                break;
        }

        return ret;
    }

    @Override
    public void setAttributes(Map<Attribute, Object> attributes) {
        //TODO: Using Java 7 NIO it should be possible to implement setting a number of attributes but does SaF allow that?
    }

    @Override
    public void setAttribute(Attribute attribute, Object value) {}

    @Override
    public String readSymbolicLink() throws IOException {
        throw new IOException("Not Implemented");
    }

    @Override
    public void createSymbolicLink(SshFile destination) throws IOException {
        throw new IOException("Not Implemented");
    }

    /**
     *  Retrieve all file info using 1 query to speed things up
     *  The only fields guaranteed to be initialized are uri and exists
     */
    private static class DocumentInfo {
        private Uri uri;
        private boolean exists;
        @Nullable
        private String documentId;
        private boolean canRead;
        private boolean canWrite;
        @Nullable
        private String mimeType;
        private boolean isDirectory;
        private boolean isFile;
        private long lastModified;
        private long length;
        @Nullable
        private String displayName;

        private static final String[] columns;

        static {
            columns = new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    //DocumentsContract.Document.COLUMN_ICON,
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.COLUMN_SIZE
            };
        }

        /*
            Based on https://github.com/rcketscientist/DocumentActivity
            Extracted from android.support.v4.provider.DocumentsContractAPI19 and android.support.v4.provider.DocumentsContractAPI21
         */
        private DocumentInfo(Context c, Uri uri)
        {
            this.uri = uri;

            try (Cursor cursor = c.getContentResolver().query(uri, columns, null, null, null)) {
                exists = cursor != null && cursor.getCount() > 0;

                if (!exists)
                    return;

                cursor.moveToFirst();

                documentId = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));

                final boolean readPerm = c.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        == PackageManager.PERMISSION_GRANTED;
                final boolean writePerm = c.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        == PackageManager.PERMISSION_GRANTED;

                final int flags = cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS));
                final boolean supportsDelete = (flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0;
                final boolean supportsCreate = (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0;
                final boolean supportsWrite = (flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
                mimeType = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                final boolean hasMime = !TextUtils.isEmpty(mimeType);

                isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                isFile = !isDirectory && hasMime;

                canRead = readPerm && hasMime;
                canWrite = writePerm && (supportsDelete || (isDirectory && supportsCreate) || (hasMime && supportsWrite));

                displayName = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                lastModified = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                length = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
            } catch (IllegalArgumentException e) {
                //File does not exist, it's probably going to be created
                exists = false;
                canWrite = true;
            }
        }
    }
}

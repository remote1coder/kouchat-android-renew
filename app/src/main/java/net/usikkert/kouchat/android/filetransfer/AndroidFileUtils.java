
/***************************************************************************
 *   Copyright 2006-2019 by Christian Ihle                                 *
 *   contact@kouchat.net                                                   *
 *                                                                         *
 *   This file is part of KouChat.                                         *
 *                                                                         *
 *   KouChat is free software; you can redistribute it and/or modify       *
 *   it under the terms of the GNU Lesser General Public License as        *
 *   published by the Free Software Foundation, either version 3 of        *
 *   the License, or (at your option) any later version.                   *
 *                                                                         *
 *   KouChat is distributed in the hope that it will be useful,            *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU      *
 *   Lesser General Public License for more details.                       *
 *                                                                         *
 *   You should have received a copy of the GNU Lesser General Public      *
 *   License along with KouChat.                                           *
 *   If not, see <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************/

package net.usikkert.kouchat.android.filetransfer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.usikkert.kouchat.net.FileToSend;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

/**
 * Utility methods for handling files on Android.
 *
 * @author Christian Ihle
 */
public class AndroidFileUtils {

    private static final Logger LOG = Logger.getLogger(AndroidFileUtils.class.getName());

    /** Buffer size used when reading files into memory. */
    private static final int READ_BUFFER_SIZE = 4096;

    private static final String URI_SCHEME_CONTENT = "content";
    private static final String URI_SCHEME_FILE = "file";

    /**
     * Gets a {@link File} reference to the file represented by the {@link Uri}.
     *
     * <p>Supports <code>content://</code> and <code>file://</code> uris.</p>
     *
     * @param uri Uri to the file to return. Can be <code>null</code>.
     * @param contentResolver The content resolver, from a context, for usage if content uri.
     * @return The file, if it's found, or <code>null</code> if not found.
     */
    public FileToSend getFileFromUri(final Uri uri, final ContentResolver contentResolver) {
        if (uri == null) {
            return null;
        }

        if (uri.getScheme().equals(URI_SCHEME_CONTENT)) {
            return getFileFromContentUri(uri, contentResolver);
        }

        if (uri.getScheme().equals(URI_SCHEME_FILE)) {
            return getFileFromFileUri(uri);
        }

        return null;
    }

    /**
     * Gets a {@link File} reference to the file represented by the content {@link Uri}.
     *
     * <p>The uri is expected to be in the following format: <code>content://media/external/images/media/22</code></p>
     *
     * <p>The <code>content</code> protocol is the only protocol supported, and is used to find files
     * in the Android media database, using a {@link ContentResolver}.</p>
     *
     * @param uri Content uri to the file to return. Can be <code>null</code>.
     * @param contentResolver The content resolver, from a context.
     * @return The file, if it's found, or <code>null</code> if not found.
     */
    FileToSend getFileFromContentUri(final Uri uri, final ContentResolver contentResolver) {
        Validate.notNull(uri, "Content uri can not be null");
        Validate.notNull(contentResolver, "ContentResolver can not be null");

        final String[] columns = new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Cursor cursor = null;

        try {
            cursor = contentResolver.query(uri, columns, null, null, null);

            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }

            cursor.moveToFirst();

            final String name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            final long size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));

            return new FileToSend(new UriInputStreamOpener(uri, contentResolver), name, size);
        }

        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Gets a {@link File} reference to the file represented by the file {@link Uri}.
     *
     * <p>The uri is expected to be in the following format:
     * <code>file:///storage/emulated/0/kouchat-1600x1600.png</code></p>
     *
     * @param uri File uri to the file to return.
     * @return The file, if it's found, or <code>null</code> if not found.
     */
    FileToSend getFileFromFileUri(final Uri uri) {
        Validate.notNull(uri, "File uri can not be null");

        final File file = new File(uri.getPath());

        if (file.exists()) {
            return new FileToSend(file);
        }

        return null;
    }

    /**
     * Reads the contents of a file into a byte array.
     *
     * <p>Used to read received image files so they can be shown inline in the
     * chat. Returns <code>null</code> if the file could not be read.</p>
     *
     * @param file The file to read.
     * @return The bytes of the file, or <code>null</code>.
     */
    public byte[] readBytes(final File file) {
        Validate.notNull(file, "File can not be null");

        try {
            return readBytes(new FileInputStream(file));
        }

        catch (final FileNotFoundException e) {
            LOG.log(Level.WARNING, "Could not find file " + file + ": " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Reads all bytes from an input stream into a byte array.
     *
     * <p>Used to read the bytes of a file to send (for example a picked image)
     * so it can be shown inline in the chat. Returns <code>null</code> if the
     * stream could not be read.</p>
     *
     * @param input The input stream to read. Will be closed.
     * @return The bytes of the stream, or <code>null</code>.
     */
    public static byte[] readBytes(final InputStream input) {
        Validate.notNull(input, "Input stream can not be null");

        try (final InputStream in = input) {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final byte[] buffer = new byte[READ_BUFFER_SIZE];
            int read;

            while ((read = in.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }

        catch (final IOException e) {
            return null;
        }
    }

    /**
     * Adds the file to the media database in Android.
     *
     * <p>It's an important step after adding a file to the file system. Without doing this, the
     * added file will not be visible in apps (like the gallery) without a reboot.</p>
     *
     * @param context A context.
     * @param fileToAdd The file to add to the database.
     */
    public void addFileToMediaDatabase(final Context context, final File fileToAdd) {
        Validate.notNull(context, "Context can not be null");
        Validate.notNull(fileToAdd, "File to add can not be null");

        android.media.MediaScannerConnection.scanFile(context,
                new String[] { fileToAdd.getAbsolutePath() }, null, null);
    }

    /**
     * Creates a new unique file in the public downloads directory of the device, with the given file
     * name as a suggestion. If the name is in use, it gets appended by a counter.
     *
     * <p>If the downloads directory is missing, it will be created.</p>
     *
     * @param fileName The suggested file name to use on the file.
     * @return A new unique file.
     */
    public File createFileInDownloadsWithAvailableName(final Context context, final String fileName) {
        Validate.notNull(context, "Context can not be null");
        Validate.notEmpty(fileName, "File name can not be empty");

        final File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);

        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }

        if (directory == null) {
            LOG.warning("Unable to access app-specific download directory.");
            return new File(fileName);
        }

        return Tools.getFileWithIncrementedName(new File(directory, fileName));
    }

    static class UriInputStreamOpener implements FileToSend.InputStreamOpener {

        private final Uri uri;
        private final ContentResolver contentResolver;

        UriInputStreamOpener(final Uri uri, final ContentResolver contentResolver) {
            this.uri = uri;
            this.contentResolver = contentResolver;
        }

        @Override
        public InputStream open() throws FileNotFoundException {
            return contentResolver.openInputStream(uri);
        }
    }
}


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

package net.usikkert.kouchat.net;

import net.usikkert.kouchat.util.Validate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Abstraction around a file to send to another user.
 *
 * @author Christian Ihle
 */
public class FileToSend {

    private final InputStreamOpener inputStreamOpener;
    private final String name;
    private final long length;

    public FileToSend(final File file) {
        Validate.notNull(file, "File to send can not be null");

        this.inputStreamOpener = new FileInputStreamOpener(file);
        this.name = file.getName();
        this.length = file.length();
    }

    public FileToSend(final InputStreamOpener inputStreamOpener,
                      final String name,
                      final long length) {
        Validate.notNull(inputStreamOpener, "InputStreamOpener can not be null");

        this.inputStreamOpener = inputStreamOpener;
        this.name = name;
        this.length = length;
    }

    /**
     * Creates a {@link FileToSend} from in-memory bytes, without needing a
     * file on disk. Each call to {@link #getInputStream()} returns a fresh
     * stream over the same data, so the same instance can be sent to several
     * users without sharing stream state.
     *
     * <p>This is used for sending clipboard screenshots and other images
     * that only exist in memory.</p>
     *
     * @param data The raw bytes to send.
     * @param name The name of the "file" presented to the receiver.
     * @return A file to send backed by the given bytes.
     */
    public static FileToSend fromBytes(final byte[] data, final String name) {
        Validate.notNull(data, "File data can not be null");
        Validate.notEmpty(name, "File name can not be null or empty");

        return new FileToSend(new ByteArrayInputStreamOpener(data), name, data.length);
    }

    public long length() {
        return length;
    }

    public String getName() {
        return name;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        return inputStreamOpener.open();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final FileToSend that = (FileToSend) o;

        if (length != that.length) {
            return false;
        }

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (int) (length ^ (length >>> 32));

        return result;
    }

    public interface InputStreamOpener {

        InputStream open() throws FileNotFoundException;
    }

    static class FileInputStreamOpener implements InputStreamOpener {

        private final File file;

        FileInputStreamOpener(final File file) {
            this.file = file;
        }

        @Override
        public InputStream open() throws FileNotFoundException {
            return new FileInputStream(file);
        }
    }

    static class ByteArrayInputStreamOpener implements InputStreamOpener {

        private final byte[] data;

        ByteArrayInputStreamOpener(final byte[] data) {
            this.data = data;
        }

        @Override
        public InputStream open() throws FileNotFoundException {
            return new ByteArrayInputStream(data);
        }
    }
}

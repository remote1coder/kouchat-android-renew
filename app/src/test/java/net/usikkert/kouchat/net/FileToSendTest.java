
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

import static org.junit.Assert.*;

import java.io.InputStream;

import net.usikkert.kouchat.junit.ExpectedException;

import org.junit.Rule;
import org.junit.Test;

/**
 * Test of {@link FileToSend}, in particular the {@link FileToSend#fromBytes}
 * factory used to send clipboard screenshots without a temporary file.
 *
 * @author Christian Ihle
 */
@SuppressWarnings("HardCodedStringLiteral")
public class FileToSendTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void fromBytesShouldCreateFileToSendWithGivenNameAndLength() {
        final byte[] data = { 1, 2, 3, 4, 5 };

        final FileToSend file = FileToSend.fromBytes(data, "screenshot.png");

        assertEquals("screenshot.png", file.getName());
        assertEquals(5, file.length());
    }

    @Test
    public void fromBytesShouldReturnFreshIndependentStreams() throws Exception {
        final byte[] data = { 10, 20, 30 };
        final FileToSend file = FileToSend.fromBytes(data, "img.png");

        final InputStream stream1 = file.getInputStream();
        final InputStream stream2 = file.getInputStream();

        // Each call must return a new stream over the same data, so the same
        // file can be sent to several users without sharing stream state.
        assertNotSame(stream1, stream2);
        assertEquals(10, stream1.read());
        assertEquals(10, stream2.read());
    }

    @Test
    public void hashCodeAndEqualsShouldBeBasedOnNameAndLengthNotContent() {
        final FileToSend file1 = FileToSend.fromBytes(new byte[] { 1, 2, 3 }, "img.png");
        final FileToSend file2 = FileToSend.fromBytes(new byte[] { 9, 9, 9 }, "img.png");

        assertEquals(file1.hashCode(), file2.hashCode());
        assertEquals(file1, file2);
    }

    @Test
    public void fromBytesShouldThrowIfDataIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("File data can not be null");

        FileToSend.fromBytes(null, "img.png");
    }

    @Test
    public void fromBytesShouldThrowIfNameIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("File name can not be null or empty");

        FileToSend.fromBytes(new byte[] { 1 }, null);
    }

    @Test
    public void fromBytesShouldThrowIfNameIsEmpty() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("File name can not be null or empty");

        FileToSend.fromBytes(new byte[] { 1 }, "   ");
    }
}

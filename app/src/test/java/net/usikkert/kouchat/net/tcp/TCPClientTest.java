
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
 *   License along with KouChat.                                            *
 *   If not, see <http://www.gnu.org/licenses/>.                            *
 ***************************************************************************/

package net.usikkert.kouchat.net.tcp;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;

import net.usikkert.kouchat.Constants;

import org.junit.Before;
import org.junit.Test;

/**
 * Test of the tcp message framing in {@link TCPClient}.
 *
 * @author Christian Ihle
 */
public class TCPClientTest {

    private TCPClient tcpClient;

    private ByteArrayOutputStream byteArrayOutputStream;

    @Before
    public void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
        byteArrayOutputStream = new ByteArrayOutputStream();
        final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        tcpClient = new TCPClient(mock(Socket.class));

        setField("outputStream", dataOutputStream);
        setField("connected", true);
    }

    @Test
    public void sendShouldWriteLengthPrefixedUtf8Message() throws IOException {
        tcpClient.send("hello world");

        final byte[] frame = byteArrayOutputStream.toByteArray();
        final DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(frame));

        // 4-byte length followed by the utf-8 bytes
        assertEquals("hello world".getBytes(Constants.MESSAGE_CHARSET).length, dataInputStream.readInt());
        assertEquals("hello world", readRest(dataInputStream));
    }

    @Test
    public void sendShouldWriteNonAsciiMessage() throws IOException {
        tcpClient.send("hëllø wörld");

        final byte[] frame = byteArrayOutputStream.toByteArray();
        final DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(frame));

        assertEquals("hëllø wörld".getBytes(Constants.MESSAGE_CHARSET).length, dataInputStream.readInt());
        assertEquals("hëllø wörld", readRest(dataInputStream));
    }

    @Test
    public void sendShouldWriteMessageLargerThanWriteUtfLimit() throws IOException {
        final StringBuilder sb = new StringBuilder(70000);

        for (int i = 0; i < 70000; i++) {
            sb.append('a');
        }

        tcpClient.send(sb.toString());

        final byte[] frame = byteArrayOutputStream.toByteArray();
        final DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(frame));

        // The frame is longer than the 64k writeUTF limit, proving the new framing supports it
        assertEquals(sb.length(), dataInputStream.readInt());

        final byte[] bytes = new byte[sb.length()];
        dataInputStream.readFully(bytes);
        assertEquals(sb.toString(), new String(bytes, Constants.MESSAGE_CHARSET));
    }

    private void setField(final String fieldName, final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = TCPClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(tcpClient, value);
    }

    private String readRest(final DataInputStream dataInputStream) throws IOException {
        final byte[] bytes = new byte[dataInputStream.available()];
        dataInputStream.readFully(bytes);

        return new String(bytes, Constants.MESSAGE_CHARSET);
    }
}

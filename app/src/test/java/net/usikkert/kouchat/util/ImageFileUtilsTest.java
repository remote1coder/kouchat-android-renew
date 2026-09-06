
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

package net.usikkert.kouchat.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Test of {@link ImageFileUtils}.
 *
 * @author Christian Ihle
 */
@SuppressWarnings("HardCodedStringLiteral")
public class ImageFileUtilsTest {

    @Test
    public void isImageFileNameShouldRecognizeCommonImageExtensions() {
        assertTrue(ImageFileUtils.isImageFileName("photo.png"));
        assertTrue(ImageFileUtils.isImageFileName("photo.jpg"));
        assertTrue(ImageFileUtils.isImageFileName("photo.jpeg"));
        assertTrue(ImageFileUtils.isImageFileName("photo.gif"));
        assertTrue(ImageFileUtils.isImageFileName("photo.bmp"));
    }

    @Test
    public void isImageFileNameShouldBeCaseInsensitive() {
        assertTrue(ImageFileUtils.isImageFileName("PHOTO.PNG"));
        assertTrue(ImageFileUtils.isImageFileName("Photo.Jpg"));
        assertTrue(ImageFileUtils.isImageFileName("PIC.BmP"));
    }

    @Test
    public void isImageFileNameShouldReturnFalseForNonImageFiles() {
        assertFalse(ImageFileUtils.isImageFileName("document.pdf"));
        assertFalse(ImageFileUtils.isImageFileName("archive.zip"));
        assertFalse(ImageFileUtils.isImageFileName("script.sh"));
        assertFalse(ImageFileUtils.isImageFileName("song.mp3"));
    }

    @Test
    public void isImageFileNameShouldReturnFalseForFilesWithoutExtension() {
        assertFalse(ImageFileUtils.isImageFileName("README"));
        assertFalse(ImageFileUtils.isImageFileName(""));
    }

    @Test
    public void isImageFileNameShouldReturnFalseForNull() {
        assertFalse(ImageFileUtils.isImageFileName(null));
    }
}

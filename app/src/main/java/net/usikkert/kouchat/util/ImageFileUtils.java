
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

import java.util.Locale;

/**
 * Utility methods for working with image files.
 *
 * <p>This class is free of desktop (AWT/Swing) dependencies so it can be
 * shared with the Android build. Image encoding/decoding is handled by the
 * platform-specific UI layers.</p>
 *
 * @author Christian Ihle
 */
public final class ImageFileUtils {

    /**
     * Private constructor. Only static methods in this class.
     */
    private ImageFileUtils() {

    }

    /**
     * Checks if the given file name has a known image file extension.
     *
     * @param fileName The file name to check, may be <code>null</code>.
     * @return <code>true</code> if the file name ends with a known image
     *         extension (png, jpg, jpeg, gif or bmp).
     */
    public static boolean isImageFileName(final String fileName) {
        if (fileName == null) {
            return false;
        }

        final String lower = fileName.toLowerCase(Locale.ENGLISH);

        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp");
    }
}

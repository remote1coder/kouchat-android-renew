
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

package net.usikkert.kouchat.android.theme;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Adjusts chat message colors so they are readable in night mode.
 *
 * <p>Message colors come from the user settings and are chosen for a light
 * background, so dark colors are lightened when the device is in night mode.
 * Colors stay unchanged in day mode.</p>
 *
 * @author Christian Ihle
 */
public final class ChatColors {

    /** Minimum perceived luminance a chat message color must have in night mode. */
    private static final int MIN_LUMINANCE = 140;

    /** How much a chat message color is blended toward white per step, when too dark. */
    private static final int BLEND_STEP = 24;

    /**
     * Private constructor, because this is a static class.
     */
    private ChatColors() {

    }

    /**
     * Adjusts a chat message color so it is readable in the current mode.
     *
     * @param context The context to check the current night mode with.
     * @param color The original message color.
     * @return The adjusted color, with full opacity.
     */
    public static int adjustForNight(final Context context, final int color) {
        if (!isNightMode(context)) {
            return color;
        }

        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        // Blend the color toward white until it is readable
        while (getLuminance(red, green, blue) < MIN_LUMINANCE) {
            red = Math.min(255, red + BLEND_STEP);
            green = Math.min(255, green + BLEND_STEP);
            blue = Math.min(255, blue + BLEND_STEP);
        }

        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Checks if the device is currently in night mode.
     *
     * @param context The context to check the current configuration with.
     * @return True if the device is in night mode.
     */
    private static boolean isNightMode(final Context context) {
        final int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * The perceived luminance of a color, on a scale from 0 (black) to 255 (white).
     *
     * @param red The red component, 0-255.
     * @param green The green component, 0-255.
     * @param blue The blue component, 0-255.
     * @return The luminance.
     */
    private static int getLuminance(final int red, final int green, final int blue) {
        return (int) (0.299 * red + 0.587 * green + 0.114 * blue);
    }
}

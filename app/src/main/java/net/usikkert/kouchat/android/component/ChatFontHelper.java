
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

package net.usikkert.kouchat.android.component;

import net.usikkert.kouchat.settings.Settings;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Applies the font family and font size chosen in the settings to the chat views.
 *
 * <p>The family is one of the Android generic font families, like
 * <code>sans-serif</code>, <code>serif</code> or <code>monospace</code>.
 * An empty family or a size of 0 means the default of the view is kept.</p>
 *
 * @author Christian Ihle
 */
public final class ChatFontHelper {

    private ChatFontHelper() {
    }

    /**
     * Applies the font settings to the given views.
     *
     * @param settings The settings with the font family and font size.
     * @param views The views to apply the font to, like the chat view and the chat input.
     */
    public static void apply(final Settings settings, final TextView... views) {
        final String fontFamily = settings.getFontName();
        final int fontSize = settings.getFontSize();

        for (final TextView view : views) {
            if (view == null) {
                continue;
            }

            if (fontFamily != null && fontFamily.length() > 0) {
                view.setTypeface(Typeface.create(fontFamily, Typeface.NORMAL));
            }

            if (fontSize > 0) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            }
        }
    }
}


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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import java.util.Timer;
import java.util.TimerTask;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

/**
 * Test of {@link TimerTools}.
 *
 * @author Christian Ihle
 */
public class TimerToolsTest {

    private TimerTools timerTools;

    @Before
    public void setUp() {
        timerTools = new TimerTools();
    }

    @Test
    public void scheduleTimerTaskShouldScheduleOneTimeTaskWithCorrectNameAndDelay() {
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() { }
        };

        try (MockedConstruction<Timer> mockedConstruction = mockConstruction(Timer.class,
                (mock, context) -> {
                    assertEquals("TheTimer", context.arguments().get(0));
                })) {

            timerTools.scheduleTimerTask("TheTimer", timerTask, 123);

            assertEquals(1, mockedConstruction.constructed().size());
            verify(mockedConstruction.constructed().get(0)).schedule(timerTask, 123);
        }
    }
}

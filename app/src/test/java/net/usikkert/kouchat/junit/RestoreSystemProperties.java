
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

package net.usikkert.kouchat.junit;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A junit rule that takes a snapshot of all system properties before each test,
 * and restores them after the test, so that changes made by the test (for example
 * via {@link System#setProperty(String, String)}) do not leak into other tests.
 *
 * <p>This replaces the old {@code system-rules} {@code RestoreSystemProperties},
 * which relied on a {@link SecurityManager} that is no longer available on modern JDKs.</p>
 *
 * @author Christian Ihle
 */
public class RestoreSystemProperties implements TestRule {

    private Map<String, String> snapshot;

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                before();

                try {
                    base.evaluate();
                } finally {
                    after();
                }
            }
        };
    }

    private void before() {
        snapshot = new HashMap<>();

        final Properties properties = System.getProperties();

        for (final String name : properties.stringPropertyNames()) {
            snapshot.put(name, properties.getProperty(name));
        }
    }

    private void after() {
        final Properties properties = System.getProperties();

        properties.clear();

        for (final Map.Entry<String, String> entry : snapshot.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
    }
}

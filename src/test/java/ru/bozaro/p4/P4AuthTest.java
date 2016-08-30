package ru.bozaro.p4;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import ru.bozaro.p4.proto.Client;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Marat Radchenko
 */
public final class P4AuthTest {

    @NotNull
    private static final String correctPassword = "SecretPassword";
    @NotNull
    private static final String wrongPassword = "banana";

    @Test(dataProvider = "all", dataProviderClass = P4TesterDataProvider.class)
    void loginWithoutPassword(@NotNull P4TesterFactory factory) throws Exception {
        try (P4Tester tester = factory.createTester()) {
            try (Client client = tester.connect()) {
                assertTrue(client.p4((message, severityHolder) -> null, "passwd", "-P", correctPassword, client.getUsername()));
            }

            try (Client client = tester.connect()) {
                assertFalse(client.p4((message, severityHolder) -> null, "changes"));
            }
        }
    }

    @Test(dataProvider = "all", dataProviderClass = P4TesterDataProvider.class)
    void loginWithWrongPassword(@NotNull P4TesterFactory factory) throws Exception {
        try (P4Tester tester = factory.createTester()) {
            try (Client client = tester.connect()) {
                assertTrue(client.p4((message, severityHolder) -> null, "passwd", "-P", correctPassword, client.getUsername()));
            }

            try (Client client = tester.connectWithPassword(wrongPassword)) {
                assertFalse(client.p4((message, severityHolder) -> null, "changes"));
            }
        }
    }

    @Test(dataProvider = "all", dataProviderClass = P4TesterDataProvider.class)
    void loginWithCorrectPassword(@NotNull P4TesterFactory factory) throws Exception {
        try (P4Tester tester = factory.createTester()) {
            try (Client client = tester.connect()) {
                assertTrue(client.p4((message, severityHolder) -> null, "passwd", "-P", correctPassword, client.getUsername()));
            }

            try (Client client = tester.connectWithPassword(correctPassword)) {
                assertTrue(client.p4((message, severityHolder) -> null, "changes"));
            }
        }
    }
}

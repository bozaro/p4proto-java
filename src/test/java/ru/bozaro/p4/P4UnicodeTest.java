package ru.bozaro.p4;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.*;
import static org.testng.Assert.assertNull;

/**
 * @author Marat Radchenko
 */
public final class P4UnicodeTest {

    @Test(dataProvider = "all", dataProviderClass = P4TesterDataProvider.class)
    void test(@NotNull P4TesterFactory factory) throws Exception {
        try (P4Tester tester = factory.createTester()) {
            tester.getClient().p4(message -> {
                assertNull(message);
                fail();
                return null;
            }, "changes");
        }
    }
}

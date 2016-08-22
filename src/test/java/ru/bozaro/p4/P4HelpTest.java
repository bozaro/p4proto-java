package ru.bozaro.p4;

import org.testng.annotations.Test;

import java.io.IOException;

/**
 * @author Marat Radchenko
 */
public final class P4HelpTest {

    @Test
    void test() throws Exception {
        try (P4Tester tester = new P4Tester()) {
            tester.getClient().p4(message -> {
                if (!"client-Message".equals(message.getFunc()))
                    throw new IOException("Unexpected call: " + message.getFunc());
                return null;
            }, "help");
        }
    }
}

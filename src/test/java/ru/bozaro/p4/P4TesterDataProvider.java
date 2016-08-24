package ru.bozaro.p4;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

/**
 * @author Marat Radchenko
 */
public final class P4TesterDataProvider {

    @NotNull
    @DataProvider
    public static Object[][] all() {
        return new Object[][]{
                new Object[]{new P4TesterFactory(true)},
                new Object[]{new P4TesterFactory(false)},
        };
    }
}

package ru.bozaro.p4;

import org.jetbrains.annotations.NotNull;

/**
 * @author Marat Radchenko
 */
public final class P4TesterFactory {

    private final boolean unicode;

    public P4TesterFactory(boolean unicode) {
        this.unicode = unicode;
    }

    @NotNull
    public P4Tester createTester() throws Exception {
        return new P4Tester(unicode);
    }

    @Override
    public String toString() {
        return "P4TesterFactory{" +
                "unicode=" + unicode +
                '}';
    }
}

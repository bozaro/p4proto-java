package ru.bozaro.p4.proto;

/**
 * @author Marat Radchenko
 */
public enum ErrorSeverity {
    None,
    Info,
    Warn,
    Failed,
    Fatal;

    public boolean isOk() {
        return compareTo(Info) <= 0;
    }

    public boolean isError() {
        return compareTo(Failed) >= 0;
    }
}

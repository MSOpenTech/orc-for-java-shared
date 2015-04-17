package com.microsoft.services.orc.interfaces;

/**
 * The interface Logger.
 */
public interface Logger {
    /**
     * Log void.
     *
     * @param content the content
     * @param logLevel the log level
     */
    public void log(String content, LogLevel logLevel);
}

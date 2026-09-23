package com.iunu.realestate.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Captures what one logger writes, formatted exactly as a pattern layout
 * would render the message. Close it to detach.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level previousLevel;

    /**
     * Pins the logger at INFO while attached, so what is captured does not
     * depend on which profile - or which earlier test - configured logging.
     */
    public LogCapture(String loggerName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
        this.previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        synchronized (appender.list) {
            return List.copyOf(appender.list);
        }
    }

    public List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }
}

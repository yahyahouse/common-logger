package com.yahya.commonlogger;

import java.io.PrintWriter;
import java.io.StringWriter;

final class ExceptionUtils {

    private ExceptionUtils() {}

    static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

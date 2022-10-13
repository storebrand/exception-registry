package com.storebrand.exceptions;

import java.util.List;

public interface ExceptionRegistry {

    // TODO: Create class to represent the throwable, and an option to register that class instead
    //       Use case is errors in client JavaScript can be sent to the backend, converted to this class
    //       and added to the registry.
    void register(Throwable throwable);

    // TODO: Create Class to represent StackTraceElement
    List<StackTraceElement> getRoots();

    // TODO: Create Class to represent exception
    List<Throwable> getExceptions(StackTraceElement element);

    // TODO: Mechanism to snooze exceptions. Think that the Throwable wrapper can also have state about being snoozed
}

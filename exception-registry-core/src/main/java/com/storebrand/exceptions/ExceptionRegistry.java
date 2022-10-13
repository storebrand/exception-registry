package com.storebrand.exceptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public interface ExceptionRegistry {

    static void register(Throwable throwable) {
        ExceptionRegistryHolder.INSTANCE.registerThrowable(throwable);
    }

    static void replaceRegistry(ExceptionRegistry registry) {
        ExceptionRegistryHolder.INSTANCE.replaceRegistry(registry);
    }

    void registerThrowable(Throwable throwable);

    void register(ExceptionInstance exception);

    ExceptionQuery query();

    // TODO: Mechanism to snooze exceptions. Think that the Throwable wrapper can also have state about being snoozed

    interface ExceptionQuery {
        default ExceptionQuery withoutParent() {
            return filter(exception -> exception.getParent().isEmpty());
        }
        default ExceptionQuery includesStackElement(StackElement stackElement) {
            return filter(exception -> exception.getStackElements().contains(stackElement));
        }
        default ExceptionQuery withType(String type) {
            return filter(exception -> Objects.equals(exception.getType(), type));
        }

        default ExceptionQuery occuredBetween(Instant fromInclusive, Instant toExclusive) {
            return filter(exception -> !exception.getOccurredAt().isBefore(fromInclusive)
                                       && exception.getOccurredAt().isBefore(toExclusive));
        }
        ExceptionQuery filter(Predicate<ExceptionInstance> filter);

        void forEach(Consumer<ExceptionInstance> exceptionConsumer);

        default <A, R> R collect(Collector<ExceptionInstance, A, R> collector) {
            A accumulator = collector.supplier().get();
            forEach(exception -> collector.accumulator().accept(accumulator, exception));
            return collector.finisher().apply(accumulator);
        }

        default List<ExceptionInstance> toList() {
            return collect(Collectors.toList());
        }
    }

    class ExceptionInstance {

        private final ExceptionInstance _parent;
        private final int _source;
        private final String _type;
        private final Instant _occurredAt;
        private final String _message;
        private final ExceptionInstance _cause;
        private final List<StackElement> _stackElements;


        public ExceptionInstance(ExceptionInstance parent, Throwable throwable, Instant occurredAt) {
            _parent = parent;
            _source = System.identityHashCode(throwable);
            _type = throwable.getClass().getName();
            _occurredAt = occurredAt;
            _cause = throwable.getCause() == null ? null : new ExceptionInstance(this, throwable.getCause(), occurredAt);
            _message = throwable.getMessage();
            StackTraceElement[] trace = throwable.getStackTrace();
            _stackElements = new ArrayList<>(trace.length);
            for (StackTraceElement stackTraceElement : trace) {
                _stackElements.add(new StackElement(stackTraceElement));
            }
        }

        public Optional<ExceptionInstance> getParent() {
            return Optional.ofNullable(_parent);
        }

        public String getType() {
            return _type;
        }

        public String getMessage() {
            return _message;
        }

        public Optional<ExceptionInstance> getCause() {
            return Optional.ofNullable(_cause);
        }

        public Instant getOccurredAt() {
            return _occurredAt;
        }

        public StackElement getRoot() {
            return _stackElements.get(0);
        }

        public List<StackElement> getStackElements() {
            return _stackElements;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExceptionInstance that = (ExceptionInstance) o;
            return _source == that._source
                   && Objects.equals(_type, that._type)
                   && Objects.equals(getRoot(), that.getRoot());
        }

        @Override
        public int hashCode() {
            return Objects.hash(_source, _type, getRoot());
        }
    }

    class StackElement {

        private final String _type;
        private final String _method;
        private final int _lineNumber;

        public StackElement(StackTraceElement stackTraceElement) {
            _type = stackTraceElement.getClassName();
            _method = stackTraceElement.getMethodName();
            _lineNumber = stackTraceElement.getLineNumber();
        }

        public StackElement(String type, String method, int lineNumber) {
            _type = type;
            _method = method;
            _lineNumber = lineNumber;
        }

        public static StackElement from(String stackElement) {
            int methodSeperator = stackElement.lastIndexOf('.');
            int lineNumberSeparator = stackElement.lastIndexOf(methodSeperator, ':');
            return new StackElement(
                    stackElement.substring(0, methodSeperator),
                    stackElement.substring(methodSeperator + 1, lineNumberSeparator),
                    Integer.parseInt(stackElement.substring(lineNumberSeparator + 1))
            );
        }

        public String getType() {
            return _type;
        }

        public String getMethod() {
            return _method;
        }

        public int getLineNumber() {
            return _lineNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StackElement that = (StackElement) o;
            return _lineNumber == that._lineNumber
                   && Objects.equals(_type, that._type)
                   && Objects.equals(_method, that._method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_type, _method, _lineNumber);
        }

        public String toString() {
            return _type + "." + _method + ":" + _lineNumber;
        }
    }



}

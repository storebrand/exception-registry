package com.storebrand.exceptions;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

enum ExceptionRegistryHolder {
    INSTANCE;

    private ExceptionRegistry _exceptionRegistry;
    private ReentrantReadWriteLock _lock = new ReentrantReadWriteLock();

    ExceptionRegistryHolder() {
        _exceptionRegistry = new ExceptionRegistryInMemory();
    }

    public void registerThrowable(Throwable throwable) {
        try {
            _lock.readLock().lock();
            _exceptionRegistry.registerThrowable(throwable);
        } finally {
            _lock.readLock().unlock();
        }
    }

    public void replaceRegistry(ExceptionRegistry registry) {
        try {
            _lock.writeLock().lock();
            // Transfer all exceptions from our current registry to the new registry
            _exceptionRegistry.query().forEach(registry::register);
            // Update te the registry, so that future calls to register goes to the new registry.
            _exceptionRegistry = registry;
        } finally {
            _lock.writeLock().unlock();
        }    }


    static class ExceptionRegistryInMemory implements ExceptionRegistry {

        private final Queue<ExceptionInstance> _exceptions = new ConcurrentLinkedQueue<>();
        private final Clock _clock = Clock.systemDefaultZone();

        @Override
        public void registerThrowable(Throwable throwable) {
            Instant now = _clock.instant();
            ExceptionInstance root = new ExceptionInstance(null, throwable, now);
            register(root);

            Throwable current = throwable.getCause();
            // Add all suppressed exceptions
            while (current != null) {
                // Register the cause of the exception
                register(new ExceptionInstance(root, current, now));
                for (Throwable suppressed : current.getSuppressed()) {
                    register(new ExceptionInstance(root, suppressed, now));
                }
                current = current.getCause();
            }
        }

        @Override
        public void register(ExceptionInstance exception) {
            _exceptions.add(exception);
        }

        @Override
        public ExceptionQuery query() {
            return new FilteringExceptionQuery(exception -> true, new ArrayList<>(_exceptions));
        }

        private static class FilteringExceptionQuery implements ExceptionQuery {

            private final Predicate<ExceptionInstance> _predicate;
            private final List<ExceptionInstance> _exceptions;

            public FilteringExceptionQuery(Predicate<ExceptionInstance> predicate,
                    List<ExceptionInstance> exceptions) {
                _predicate = predicate;
                _exceptions = exceptions;
            }

            @Override
            public ExceptionQuery filter(Predicate<ExceptionInstance> filter) {
                return new FilteringExceptionQuery(_predicate.and(filter), _exceptions);
            }

            @Override
            public void forEach(Consumer<ExceptionInstance> exceptionConsumer) {
                _exceptions.stream().filter(_predicate).forEach(exceptionConsumer);
            }
        }
    }
}

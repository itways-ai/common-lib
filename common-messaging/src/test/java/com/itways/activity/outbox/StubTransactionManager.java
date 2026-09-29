package com.itways.activity.outbox;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** A transaction manager without a resource: real propagation and synchronization, counted begins. */
final class StubTransactionManager extends AbstractPlatformTransactionManager {

    private record Tx(boolean existing) {
    }

    final AtomicInteger begun = new AtomicInteger();
    private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

    @Override
    protected Object doGetTransaction() {
        return new Tx(active.get());
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((Tx) transaction).existing();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        begun.incrementAndGet();
        active.set(true);
    }

    @Override
    protected Object doSuspend(Object transaction) {
        active.set(false);
        return Boolean.TRUE;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        active.set(true);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        active.set(false);
    }
}

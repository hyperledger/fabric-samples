/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class Utils {
    public interface ProtoCall<T> extends Callable<T> {
        @Override
        T call() throws InvalidProtocolBufferException;
    }

    public static <T> T getCachedProto(final AtomicReference<T> cache, final ProtoCall<T> call) throws InvalidProtocolBufferException {
        try {
            return cache.updateAndGet(current -> current != null ? current : asSupplier(call).get());
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof InvalidProtocolBufferException) {
                throw (InvalidProtocolBufferException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    public static <T> Supplier<T> asSupplier(final Callable<T> call) {
        return () -> {
            try {
                return call.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    private Utils() { }
}

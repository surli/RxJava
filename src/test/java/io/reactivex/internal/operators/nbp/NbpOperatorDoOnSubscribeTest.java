/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.nbp;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.*;

import org.junit.Test;

import io.reactivex.NbpObservable;
import io.reactivex.NbpObservable.*;
import io.reactivex.internal.disposables.EmptyDisposable;

public class NbpOperatorDoOnSubscribeTest {

    @Test
    public void testDoOnSubscribe() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        NbpObservable<Integer> o = NbpObservable.just(1).doOnSubscribe(s -> {
                count.incrementAndGet();
        });

        o.subscribe();
        o.subscribe();
        o.subscribe();
        assertEquals(3, count.get());
    }

    @Test
    public void testDoOnSubscribe2() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        NbpObservable<Integer> o = NbpObservable.just(1).doOnSubscribe(s -> {
                count.incrementAndGet();
        }).take(1).doOnSubscribe(s -> {
                count.incrementAndGet();
        });

        o.subscribe();
        assertEquals(2, count.get());
    }

    @Test
    public void testDoOnUnSubscribeWorksWithRefCount() throws Exception {
        final AtomicInteger onSubscribed = new AtomicInteger();
        final AtomicInteger countBefore = new AtomicInteger();
        final AtomicInteger countAfter = new AtomicInteger();
        final AtomicReference<NbpSubscriber<? super Integer>> sref = new AtomicReference<>();
        NbpObservable<Integer> o = NbpObservable.create(new NbpOnSubscribe<Integer>() {

            @Override
            public void accept(NbpSubscriber<? super Integer> s) {
                s.onSubscribe(EmptyDisposable.INSTANCE);
                onSubscribed.incrementAndGet();
                sref.set(s);
            }

        }).doOnSubscribe(s -> {
                countBefore.incrementAndGet();
        }).publish().refCount()
        .doOnSubscribe(s -> {
                countAfter.incrementAndGet();
        });

        o.subscribe();
        o.subscribe();
        o.subscribe();
        assertEquals(1, countBefore.get());
        assertEquals(1, onSubscribed.get());
        assertEquals(3, countAfter.get());
        sref.get().onComplete();
        o.subscribe();
        o.subscribe();
        o.subscribe();
        assertEquals(2, countBefore.get());
        assertEquals(2, onSubscribed.get());
        assertEquals(6, countAfter.get());
    }

}
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

package io.reactivex.internal.operators;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;
import org.reactivestreams.*;

import io.reactivex.Observable;
import io.reactivex.TestHelper;
import io.reactivex.internal.schedulers.IOScheduler;
import io.reactivex.internal.subscriptions.EmptySubscription;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

public class OperatorMergeMaxConcurrentTest {

    Subscriber<String> stringObserver;

    @Before
    public void before() {
        stringObserver = TestHelper.mockSubscriber();
    }

    @Test
    public void testWhenMaxConcurrentIsOne() {
        for (int i = 0; i < 100; i++) {
            List<Observable<String>> os = new ArrayList<>();
            os.add(Observable.just("one", "two", "three", "four", "five").subscribeOn(Schedulers.newThread()));
            os.add(Observable.just("one", "two", "three", "four", "five").subscribeOn(Schedulers.newThread()));
            os.add(Observable.just("one", "two", "three", "four", "five").subscribeOn(Schedulers.newThread()));

            List<String> expected = Arrays.asList("one", "two", "three", "four", "five", "one", "two", "three", "four", "five", "one", "two", "three", "four", "five");
            Iterator<String> iter = Observable.merge(os, 1).toBlocking().iterator();
            List<String> actual = new ArrayList<>();
            while (iter.hasNext()) {
                actual.add(iter.next());
            }
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testMaxConcurrent() {
        for (int times = 0; times < 100; times++) {
            int observableCount = 100;
            // Test maxConcurrent from 2 to 12
            int maxConcurrent = 2 + (times % 10);
            AtomicInteger subscriptionCount = new AtomicInteger(0);

            List<Observable<String>> os = new ArrayList<>();
            List<SubscriptionCheckObservable> scos = new ArrayList<>();
            for (int i = 0; i < observableCount; i++) {
                SubscriptionCheckObservable sco = new SubscriptionCheckObservable(subscriptionCount, maxConcurrent);
                scos.add(sco);
                os.add(Observable.create(sco));
            }

            Iterator<String> iter = Observable.merge(os, maxConcurrent).toBlocking().iterator();
            List<String> actual = new ArrayList<>();
            while (iter.hasNext()) {
                actual.add(iter.next());
            }
            //            System.out.println("actual: " + actual);
            assertEquals(5 * observableCount, actual.size());
            for (SubscriptionCheckObservable sco : scos) {
                assertFalse(sco.failed);
            }
        }
    }

    private static class SubscriptionCheckObservable implements Publisher<String> {

        private final AtomicInteger subscriptionCount;
        private final int maxConcurrent;
        volatile boolean failed = false;

        SubscriptionCheckObservable(AtomicInteger subscriptionCount, int maxConcurrent) {
            this.subscriptionCount = subscriptionCount;
            this.maxConcurrent = maxConcurrent;
        }

        @Override
        public void subscribe(final Subscriber<? super String> t1) {
            t1.onSubscribe(EmptySubscription.INSTANCE);
            new Thread(new Runnable() {

                @Override
                public void run() {
                    if (subscriptionCount.incrementAndGet() > maxConcurrent) {
                        failed = true;
                    }
                    t1.onNext("one");
                    t1.onNext("two");
                    t1.onNext("three");
                    t1.onNext("four");
                    t1.onNext("five");
                    // We could not decrement subscriptionCount in the unsubscribe method
                    // as "unsubscribe" is not guaranteed to be called before the next "subscribe".
                    subscriptionCount.decrementAndGet();
                    t1.onComplete();
                }

            }).start();
        }

    }
    
    @Test
    public void testMergeALotOfSourcesOneByOneSynchronously() {
        int n = 10000;
        List<Observable<Integer>> sourceList = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sourceList.add(Observable.just(i));
        }
        Iterator<Integer> it = Observable.merge(Observable.fromIterable(sourceList), 1).toBlocking().iterator();
        int j = 0;
        while (it.hasNext()) {
            assertEquals((Integer)j, it.next());
            j++;
        }
        assertEquals(j, n);
    }
    @Test
    public void testMergeALotOfSourcesOneByOneSynchronouslyTakeHalf() {
        int n = 10000;
        List<Observable<Integer>> sourceList = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sourceList.add(Observable.just(i));
        }
        Iterator<Integer> it = Observable.merge(Observable.fromIterable(sourceList), 1).take(n / 2).toBlocking().iterator();
        int j = 0;
        while (it.hasNext()) {
            assertEquals((Integer)j, it.next());
            j++;
        }
        assertEquals(j, n / 2);
    }
    
    @Test
    public void testSimple() {
        for (int i = 1; i < 100; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Observable<Integer>> sourceList = new ArrayList<>(i);
            List<Integer> result = new ArrayList<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Observable.just(j));
                result.add(j);
            }
            
            Observable.merge(sourceList, i).subscribe(ts);
        
            ts.assertNoErrors();
            ts.assertTerminated();
            ts.assertValueSequence(result);
        }
    }
    @Test
    public void testSimpleOneLess() {
        for (int i = 2; i < 100; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Observable<Integer>> sourceList = new ArrayList<>(i);
            List<Integer> result = new ArrayList<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Observable.just(j));
                result.add(j);
            }
            
            Observable.merge(sourceList, i - 1).subscribe(ts);
        
            ts.assertNoErrors();
            ts.assertTerminated();
            ts.assertValueSequence(result);
        }
    }
    @Test//(timeout = 20000)
    public void testSimpleAsyncLoop() {
        IOScheduler ios = (IOScheduler)Schedulers.io();
        int c = ios.size();
        for (int i = 0; i < 200; i++) {
            testSimpleAsync();
            int c1 = ios.size();
            if (c + 60 < c1) {
                throw new AssertionError("Worker leak: " + c + " - " + c1);
            }
        }
    }
    @Test(timeout = 10000)
    public void testSimpleAsync() {
        for (int i = 1; i < 50; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Observable<Integer>> sourceList = new ArrayList<>(i);
            Set<Integer> expected = new HashSet<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Observable.just(j).subscribeOn(Schedulers.io()));
                expected.add(j);
            }
            
            Observable.merge(sourceList, i).subscribe(ts);
        
            ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
            ts.assertNoErrors();
            Set<Integer> actual = new HashSet<>(ts.values());
            
            assertEquals(expected, actual);
        }
    }
    @Test(timeout = 10000)
    public void testSimpleOneLessAsyncLoop() {
        for (int i = 0; i < 200; i++) {
            testSimpleOneLessAsync();
        }
    }
    @Test(timeout = 10000)
    public void testSimpleOneLessAsync() {
        long t = System.currentTimeMillis();
        for (int i = 2; i < 50; i++) {
            if (System.currentTimeMillis() - t > TimeUnit.SECONDS.toMillis(9)) {
                break;
            }
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Observable<Integer>> sourceList = new ArrayList<>(i);
            Set<Integer> expected = new HashSet<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Observable.just(j).subscribeOn(Schedulers.io()));
                expected.add(j);
            }
            
            Observable.merge(sourceList, i - 1).subscribe(ts);
        
            ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
            ts.assertNoErrors();
            Set<Integer> actual = new HashSet<>(ts.values());
            
            assertEquals(expected, actual);
        }
    }
    @Test(timeout = 5000)
    public void testBackpressureHonored() throws Exception {
        List<Observable<Integer>> sourceList = new ArrayList<>(3);
        
        sourceList.add(Observable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Observable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Observable.range(0, 100000).subscribeOn(Schedulers.io()));
        
        final CountDownLatch cdl = new CountDownLatch(5);
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>((Long)null) {
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                cdl.countDown();
            }
        };
        
        Observable.merge(sourceList, 2).subscribe(ts);
        
        ts.request(5);
        
        cdl.await();
        
        ts.assertNoErrors();
        ts.assertValueCount(5);
        ts.assertNotComplete();
        
        ts.dispose();
    }
    @Test(timeout = 5000)
    public void testTake() throws Exception {
        List<Observable<Integer>> sourceList = new ArrayList<>(3);
        
        sourceList.add(Observable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Observable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Observable.range(0, 100000).subscribeOn(Schedulers.io()));
        
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Observable.merge(sourceList, 2).take(5).subscribe(ts);
        
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(5);
    }
}
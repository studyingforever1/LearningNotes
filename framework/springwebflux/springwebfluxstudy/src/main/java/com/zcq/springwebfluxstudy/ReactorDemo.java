package com.zcq.springwebfluxstudy;

import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class ReactorDemo {
    public static void main(String[] args) throws Exception {
        testMono();
        testFlux();

    }

    public static void testMono() {
//        Mono<String> hello = Mono.just("hello");
//        hello.subscribe(System.out::println);

//        Mono<Object> objectMono = Mono.justOrEmpty(null);
//        objectMono.subscribe(System.out::println);

//        Mono<Object> objectMono = Mono.justOrEmpty(Optional.of("hello"));
//        objectMono.subscribe(System.out::println);

//        Mono.defer(new Supplier<Mono<String>>() {
//            @Override
//            public Mono<String> get() {
//                return Mono.fromCallable(() -> "hello");
//            }
//        }).subscribe(System.out::println);

    }

    public static void testFlux() throws InterruptedException {
//        Flux<String> just = Flux.just("hello", "world");
//        just.subscribe(System.out::println);
//
//        Flux<String> stringFlux = Flux.fromArray(new String[]{"a", "b", "c"});
//        stringFlux.subscribe(System.out::println);
//
//        Flux<Integer> flux = Flux.fromIterable(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
//        flux.subscribe(System.out::println);
//
//        Flux<Integer> range = Flux.range(1000, 5);
//        range.subscribe(System.out::println);

//        Flux.from(new Publisher<String>() {
//            @Override
//            public void subscribe(Subscriber<? super String> s) {
//                for (int i = 0; i < 10; i++) {
//                    s.onNext("hello" + i);
//                }
//                s.onComplete();
//            }
//        }).subscribe(System.out::println, System.err::println, () -> System.out.println("done"));

//        Flux.range(100, 10)
//                .filter(t -> t % 3 == 0)
//                .map(t -> t + " hello")
//                .doOnNext(System.out::println)
//                .doOnSubscribe(s -> {
//                    System.out.println(s);
//                    System.out.println("订阅了");
//                })
//                .subscribe();

//        Flux.just("hello","world","!")
//                .subscribe(new Subscriber<String>() {
//                    Subscription subscription;
//                    @Override
//                    public void onSubscribe(Subscription s) {
//                        System.out.println("subscribe");
//                        s.request(1);
//                        subscription = s;
//                        System.out.println("赋值");
//                    }
//
//                    @Override
//                    public void onNext(String s) {
//                        System.out.println("onNext");
//                        System.out.println(s);
//                        subscription.request(1);
//                    }
//
//                    @Override
//                    public void onError(Throwable t) {
//                        System.out.println("onError");
//                    }
//
//                    @Override
//                    public void onComplete() {
//                        System.out.println("onComplete");
//                    }
//                });

//        Flux.range(1, 10)
//                .map(e -> e + " hello")
//                .subscribe(System.out::println);

//        Flux.range(1, 10)
//                .map(s -> s + " hello")
//                .index()
//                .subscribe(s -> {
//                    System.out.println(s.getT1());
//                    System.out.println(s.getT2());
//                });

//        Flux.range(1, 10)
//                .map(s -> s + " hello")
//                .timestamp()
//                .subscribe(s -> {
//                    System.out.println(s.getT1());
//                    System.out.println(s.getT2());
//                });

//        Flux.interval(Duration.ofMillis(500))
//                .map(s -> s + " hello")
//                .doOnNext(System.out::println)
//                .skipUntilOther(Mono.just("skip").delayElement(Duration.ofSeconds(3)))
//                .takeUntilOther(Mono.just("take").delayElement(Duration.ofSeconds(6)))
//                .subscribe(s -> System.out.println("doOnNext" + s));
//
//        Thread.sleep(Duration.ofSeconds(10).toMillis());

//        Flux.just(1,34,56,43,23,3)
//                .collectSortedList(Comparator.reverseOrder())
//                .subscribe(System.out::println);

//        Flux.range(1, 10)
//                .collectMap(c -> c + "key", v -> v + "value")
//                .subscribe(System.out::println);

//        Flux.range(1, 1)
//                .repeat(3)
//                .subscribe(System.out::println);

//        Flux.empty().defaultIfEmpty("hello msb").subscribe(System.out::println);

//        Flux.just(1,2,3)
//                .repeat(3)
//                .distinct()
//                .subscribe(System.out::println);

//        Flux.just(1,1,1,2,2,2,3,3,3,1,1,1,2,2,2)
//                .distinctUntilChanged()
//                .subscribe(System.out::print);
//        System.out.println();
//        System.out.println("===============");
//        Flux.just(1,1,1,2,2,2,3,3,3,1,1,1,2,2,2)
//                .distinct()
//                .subscribe(System.out::print);

//        Flux.range(1, 10)
//                .doOnNext(System.out::println)
//                .any(i -> i % 2 == 0)
//                .subscribe(System.out::println);

//        Flux.range(1, 10)
//                .reduce(0, Integer::sum)
//                .subscribe(System.out::println);

//        Flux.range(1, 10)
//                .scan(0, Integer::sum)
//                .subscribe(System.out::println);
//        int arrayLen = 5;
//        Flux.just(1, 2, 3, 4, 5, 6)
//                .index()
//                .scan(new int[5], (array, entry) -> {
//                    array[(int) (entry.getT1() % arrayLen)] = entry.getT2();
//                    return array;
//                })
//                .skip(arrayLen)
//                .map(e -> Arrays.stream(e).average().orElseThrow())
//                .subscribe(System.out::println);


//        Flux.range(1,50)
//                .doOnNext(System.out::println)
//                .thenEmpty(Mono.empty())
//                .subscribe(System.out::println);


//        Flux.zip(
//                Flux.range(1, 10).delayElements(Duration.ofSeconds(1))
//                        .doOnNext(System.out::println),
//                Flux.range(100, 100).delayElements(Duration.ofSeconds(1))
//                        .doOnNext(System.out::println)
//        ).subscribe(System.out::println);
//
//        Thread.sleep(Duration.ofSeconds(100).toMillis());


//        Random random = new Random();
//        Flux.just(Arrays.asList(1, 2, 3), Arrays.asList("a", "b", "c", "d"), Arrays.asList(7, 8, 9))
//                .doOnNext(System.out::println)
//                .flatMap(item -> {
//                            System.out.println(item);
//                            return Flux.fromIterable(item)
//                                    .doOnSubscribe(subscription -> System.out.println("已经订阅"))
//                                    .delayElements(Duration.ofMillis(random.nextInt(100) + 100));
//                        }
//
//                ).subscribe(System.out::println);
//
//        Thread.sleep(10 * 1000);

//        Random random = new Random();
//        Flux.just(Arrays.asList(1, 2, 3), Arrays.asList("a", "b", "c", "d"), Arrays.asList(7, 8, 9))
//                .doOnNext(System.out::println)
//                .concatMap(item -> {
//                            System.out.println(item);
//                            return Flux.fromIterable(item)
//                                    .doOnSubscribe(subscription -> System.out.println("已经订阅"))
//                                    .delayElements(Duration.ofMillis(random.nextInt(100) + 100));
//                        }
//
//                ).subscribe(System.out::println);
//
//        Thread.sleep(10 * 1000);

//        Random random = new Random();
//        Flux.just(Arrays.asList(1, 2, 3), Arrays.asList("a", "b", "c", "d"), Arrays.asList(7, 8, 9))
//                .doOnNext(System.out::println)
//                .flatMapSequential(item -> {
//                            System.out.println(item);
//                            return Flux.fromIterable(item)
//                                    .doOnSubscribe(subscription -> System.out.println("已经订阅"))
//                                    .delayElements(Duration.ofMillis(random.nextInt(100) + 100));
//                        }
//
//                ).subscribe(System.out::println);
//
//        Thread.sleep(10 * 1000);


//        Flux.range(1, 100)
//                .buffer(10)
//                .subscribe(System.out::println);

//        Flux.range(101, 20)
//                .windowUntil(ReactorDemo::isPrime, false)
//                .subscribe(
//                        window ->
//                                window.collectList()
//                                        .subscribe(
//                                                item -> System.out.println("window:" + item)
//                                        )
//                );


//        Integer integer2 = Flux.just(1, 2, 3)
//                .delayElements(Duration.ofSeconds(1))
//                .doOnNext(item -> System.out.println("onNext:" + item))
//                .blockLast();
//        System.out.println("==========");
//        System.out.println(integer2);
//        System.out.println("==========");


//        Flux.just(1, 2, 3)
//                .delayElements(Duration.ofMillis(1000))
//                .doOnNext(c -> System.out.println("doOnNext: " + Thread.currentThread().getName()))
//                .publishOn(Schedulers.parallel())
//                .concatWith(Flux.error(new Exception("手动异常")))
//                .materialize()
//                .doOnEach(item -> System.out.println("doOnEach: " + Thread.currentThread().getName() + item.isOnComplete()))
//                .log()
//                .subscribe(c -> System.out.println("sub: " + Thread.currentThread().getName() + c));
//        Thread.sleep(10 * 1000);


//        Flux.push(new Consumer<FluxSink<Integer>>() {
//            @Override
//            public void accept(FluxSink<Integer> fluxSink) {
//                System.out.println(Thread.currentThread().getName());
//                IntStream.range(1, 10).forEach(fluxSink::next);
//            }
//        }).subscribe(new Consumer<Integer>() {
//            @Override
//            public void accept(Integer integer) {
//                System.out.println(Thread.currentThread().getName());
//                System.out.println(integer);
//            }
//        });


//        Flux.generate(
//                        // 通过Callable提供初始状态实例
//                        () -> Tuples.of(0L, 1L), // 负责斐波拉契数列
//                        // 函数第一个参数数据、函数第二个参数类型 、返回值
//                        (state, sink) -> {
//                            System.out.println("生成的数字：" + state.getT2());
//                            sink.next(state.getT1());
//                            long nextValue = state.getT1() + state.getT2();
//                            return Tuples.of(state.getT2(), nextValue);
//                        }).delayElements(Duration.ofMillis(500))
//                .take(10)
//                .subscribe(System.out::println);
//        Thread.sleep(5000);


//        CountDownLatch latch = new CountDownLatch(1);
//        Flux.range(1,1000)
//                .delayElements(Duration.ofMillis(10))
//                .onBackpressureLatest()
//                .delayElements(Duration.ofMillis(100))
//                .subscribe(
//                        System.out::println,
//                        ex ->{
//                            System.out.println(ex);
//                            latch.countDown();
//                        },
//                        () ->{
//                            System.out.println("处理完毕");
//                            latch.countDown();
//                        }
//                );
//        latch.await();
//        System.out.println("main结束");

//
//        Flux<Integer> source = Flux.range(0,3)
//                .doOnSubscribe(s -> System.out.println("对冷发布者的新订阅票据：" + s));
//        ConnectableFlux<Integer> conn = source.publish();
//        conn.subscribe(item -> System.out.println("[subscriber 1] onNext:" + item));
//        conn.subscribe(item -> System.out.println("[subscriber 2] onNext:" + item));
//        System.out.println("所有订阅者都准备建立连接");
//        conn.connect();

    }

//    private static boolean isPrime(Integer integer) {
//        double sqrt = Math.sqrt(integer);
//        if (integer < 2) {
//            return false;
//        }
//        if (integer == 2 || integer == 3) {
//            return true;
//        }
//        if (integer % 2 == 0) {
//            return false;
//        }
//        for (int i = 3; i <= sqrt; i++) {
//            if (integer % i == 0) {
//                return false;
//            }
//        }
//        return true;
//    }
}



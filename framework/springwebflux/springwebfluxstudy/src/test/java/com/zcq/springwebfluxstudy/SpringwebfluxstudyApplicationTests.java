package com.zcq.springwebfluxstudy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SpringBootTest
class SpringwebfluxstudyApplicationTests {

    @Test
    void contextLoads() {
    }

    public static void main(String[] args) {
        middle();
    }

    private static void create() {
        new Random().ints().limit(10).forEach(System.out::println);
        IntStream.rangeClosed(1, 10).forEach(System.out::println);
        Stream.generate(Math::random).limit(10).forEach(System.out::println);
    }

    private static void middle() {
        String str = "my name is jack";
//        Stream.of(str.split(" ")).mapToInt(String::length).forEach(System.out::println);
//        Stream.of(str.split(" ")).flatMapToInt(String::chars).forEach(System.out::println);

//        new Random().ints().forEach(System.out::println);
//        str.chars().parallel().forEach(c -> System.out.print((char) c));
//        str.chars().parallel().forEachOrdered(c -> System.out.print((char) c));

//        Stream.of(str.split(" ")).reduce((a, b) -> a + "|" + b).ifPresent(System.out::println);
//        Stream.of(str.split(" ")).max(Comparator.comparingInt(String::length)).ifPresent(System.out::println);

//        new Random().ints().findFirst().ifPresent(System.out::println);


//        IntStream.rangeClosed(1, 100)
//                .parallel()
//                .peek(c -> System.out.println(Thread.currentThread().getName() + ":" + c))
//                .max().ifPresent(System.out::println);

//        ForkJoinPool forkJoinPool = new ForkJoinPool(20);
//        try {
//            forkJoinPool.submit(() -> IntStream.rangeClosed(1, 100)
//                    .parallel()
//                    .peek(c -> System.out.println(Thread.currentThread().getName() + ":" + c))
//                    .max().ifPresent(System.out::println)).get();
//        } catch (InterruptedException | ExecutionException e) {
//            e.printStackTrace();
//        }
//        forkJoinPool.shutdown();



    }
}

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class Test {

    public static void main(String[] args) {
        EventExecutor eventExecutor = new NioEventLoopGroup().next();
        DefaultPromise<Object> promise = new DefaultPromise<>(eventExecutor);

        promise.addListener(new GenericFutureListener<Future<? super Object>>() {
            @Override
            public void operationComplete(Future<? super Object> future) throws Exception {
                System.out.println(Thread.currentThread().getName());
                System.out.println(future.get());
            }
        });

        promise.setSuccess("hello");



    }

}

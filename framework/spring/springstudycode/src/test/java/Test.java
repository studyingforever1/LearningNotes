import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;

public class Test {
    public static void main(String[] args) {
        //通过应用类加载器可以加载到java.class.path下的文件
        ClassLoader classLoader = com.zcq.demo.test.Test.class.getClassLoader();
        URL resource = classLoader.getResource("hello.c");
        System.out.println(resource);
        //通常java.class.path的所有扫描路径保存在properties中
        Properties properties = System.getProperties();
        String property = System.getProperty("java.class.path");
        String[] split = property.split(";");
        System.out.println(Arrays.toString(split));



    }
}

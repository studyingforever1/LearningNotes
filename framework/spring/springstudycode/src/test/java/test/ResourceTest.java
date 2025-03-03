package test;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ResourceTest {

    @Resource
    ResourceLoader resourceLoader;

    @Resource
    Environment environment;

    private void test() {
        resourceLoader.getResource("xxx.yml");
        environment.resolvePlaceholders("${user.name}");
    }

}

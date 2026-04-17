# springMVC

## 拦截器和过滤器的区别

```text
请求进来
   ↓
Filter（过滤器）        ← Servlet 规范，Tomcat 层面
   ↓
DispatcherServlet
   ↓
Interceptor（拦截器）   ← Spring MVC，能拿到 Handler 信息
   ↓
Controller
```

### 过滤器 Filter

Servlet 规范定义，与 Spring 无关，任何 Web 框架都有。

```java
@Component
public class MyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        // 前置处理
        System.out.println("Filter before");

        chain.doFilter(request, response); // 放行

        // 后置处理（响应已写入）
        System.out.println("Filter after");
    }
}
```

特点：

- 能拿到原始 `ServletRequest` / `ServletResponse`
- 可以**替换 request/response**（如包装成可重复读的流）
- 拿不到 Spring 的任何上下文，不知道请求对应哪个 Controller
- 适合：**日志、编码、跨域、限流、认证 Token 解析**

**注册方式**

- `@Component` Spring Boot 会自动扫描注册，但**无法控制顺序和路径**。

  ```java
  @Component
  public class MyFilter implements Filter {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response,
                           FilterChain chain) throws IOException, ServletException {
          chain.doFilter(request, response);
      }
  }
  ```

- `FilterRegistrationBean`

  ```java
  @Configuration
  public class FilterConfig {
  
      @Bean
      public FilterRegistrationBean<MyFilter> myFilter() {
          FilterRegistrationBean<MyFilter> bean = new FilterRegistrationBean<>();
          bean.setFilter(new MyFilter());
          bean.addUrlPatterns("/api/*");  // 指定路径
          bean.setOrder(1);               // 控制顺序
          bean.setName("myFilter");
          return bean;
      }
  }
  ```

- `@WebFilter + @ServletComponentScan`

  ```java
  @WebFilter(urlPatterns = "/api/*")
  public class MyFilter implements Filter {
      // ...
  }
  
  @SpringBootApplication
  @ServletComponentScan  // 启动类加这个才生效
  public class Application {
      public static void main(String[] args) {
          SpringApplication.run(Application.class, args);
      }
  }
  ```

  





### 拦截器 Interceptor

Spring MVC 定义，只在 DispatcherServlet 内生效。

```java
@Component
public class MyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // handler 就是目标 Controller 方法
        HandlerMethod method = (HandlerMethod) handler;
        System.out.println("目标方法: " + method.getMethod().getName());
        return true; // false 则中断，不往下走
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // Controller 执行完，视图渲染前
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 视图渲染完，响应已发出，适合清理资源
    }
}
```

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MyInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login");
    }
}
```

特点：

- 能拿到 `HandlerMethod`，知道目标 Controller 和方法
- 能读取方法上的**注解**（如权限注解）
- 天然支持 Spring 的 IOC，可以直接注入 Bean
- 适合：**权限校验、操作日志、登录检查**










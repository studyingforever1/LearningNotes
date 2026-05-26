# Java 泛型深度解析

> 本文档持续更新，后续相关提问也会追加在文末。

---

## 目录

1. [泛型基础](#1-泛型基础)
2. [类型擦除](#2-类型擦除)
3. [通配符与边界](#3-通配符与边界)
4. [Type 体系](#4-type-体系)
5. [Class 与泛型](#5-class-与泛型)
6. [TypeToken 与泛型类型捕获](#6-typetoken-与泛型类型捕获)
7. [泛型与反射](#7-泛型与反射)
8. [常见陷阱与最佳实践](#8-常见陷阱与最佳实践)

---

## 1. 泛型基础

### 1.1 为什么需要泛型

泛型在 Java 5 引入，核心目的是**编译期类型安全**。没有泛型时：

```java
// JDK 5 之前 —— 全靠 Object，运行时才报 ClassCastException
List list = new ArrayList();
list.add("hello");
list.add(42);           // 合法，但埋下隐患
String s = (String) list.get(1);  // 运行时：ClassCastException!

// 有了泛型 —— 编译期就报错
List<String> list = new ArrayList<>();
list.add(42);           // 编译错误：incompatible types
```

### 1.2 泛型的三种形式

| 形式 | 示例 | 说明 |
|------|------|------|
| 泛型类 | `class Box<T>` | 类级别声明类型参数 |
| 泛型接口 | `interface Comparable<T>` | 接口级别声明类型参数 |
| 泛型方法 | `<T> T identity(T t)` | 方法级别声明类型参数，独立于类 |

```java
// 泛型类
class Box<T> {
    private T value;
    public Box(T value) { this.value = value; }
    public T get() { return value; }
}

// 泛型方法（注意 <T> 在返回类型前）
class Utils {
    public static <T> List<T> listOf(T... items) {
        return Arrays.asList(items);
    }
}

// 多类型参数
class Pair<A, B> {
    public final A first;
    public final B second;
    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
}
```

### 1.3 类型参数命名惯例

| 字母 | 含义 |
|------|------|
| `T` | Type，通用类型 |
| `E` | Element，集合元素 |
| `K` / `V` | Key / Value，键值对 |
| `N` | Number，数字类型 |
| `R` | Return，返回值类型 |
| `S`, `U`, `V` | 第二、三、四个类型参数 |

---

## 2. 类型擦除

### 2.1 什么是类型擦除

Java 泛型是**编译时特性**，编译后字节码中不保留泛型类型信息（除特殊场景），这一过程称为**类型擦除（Type Erasure）**。

```
源码：List<String>  →  字节码：List（raw type）
源码：List<Integer> →  字节码：List（raw type）
```

验证：

```java
List<String>  a = new ArrayList<>();
List<Integer> b = new ArrayList<>();

System.out.println(a.getClass() == b.getClass()); // true！
System.out.println(a.getClass());                 // class java.util.ArrayList
```

### 2.2 擦除规则

编译器按以下规则进行擦除：

| 原始类型 | 擦除后 |
|----------|--------|
| `T`（无界） | `Object` |
| `T extends Foo` | `Foo` |
| `T extends Foo & Bar` | `Foo`（第一个上界） |
| `List<String>` | `List` |
| `Map<K, V>` | `Map` |

```java
// 擦除示例：编译器生成的等效代码
class Box<T> {
    private T value;
    public T get() { return value; }
}
// 擦除后等效于：
class Box {
    private Object value;
    public Object get() { return value; }
}

// 有上界时：
class NumberBox<T extends Number> {
    public T get() { return value; }
}
// 擦除后等效于：
class NumberBox {
    public Number get() { return value; }
}
```

### 2.3 桥方法（Bridge Method）

类型擦除可能破坏多态，编译器会自动生成**桥方法**修复：

```java
interface Comparable<T> {
    int compareTo(T o);
}

class IntBox implements Comparable<IntBox> {
    int val;
    @Override
    public int compareTo(IntBox o) { return Integer.compare(val, o.val); }
}

// 编译器实际生成（用 javap -v 可见）：
// public int compareTo(IntBox o)  —— 真实实现
// public int compareTo(Object o)  —— 桥方法，调用上面那个，满足接口擦除后的签名
```

### 2.4 类型擦除的限制

```java
// ❌ 不能用类型参数创建实例
T obj = new T();

// ❌ 不能创建泛型数组
T[] arr = new T[10];
List<String>[] lists = new List<String>[10];

// ❌ 不能用 instanceof 判断泛型类型
if (obj instanceof List<String>) { }  // 编译错误

// ❌ 不能捕获/抛出泛型异常
class MyException<T> extends Exception { }  // 编译错误

// ✅ 可以 instanceof 检查原始类型
if (obj instanceof List) { }

// ✅ 通过反射传入 Class<T> 绕过实例化限制
<T> T create(Class<T> clazz) throws Exception {
    return clazz.newInstance();
}
```

---

## 3. 通配符与边界

### 3.1 三种通配符形式

| 语法 | 名称 | 含义 |
|------|------|------|
| `<?>` | 无界通配符 | 任意类型 |
| `<? extends T>` | 上界通配符 | T 或 T 的子类 |
| `<? super T>` | 下界通配符 | T 或 T 的父类 |

### 3.2 PECS 原则

**Producer Extends, Consumer Super**：
- 从集合中**读取**（生产数据）→ 用 `extends`
- 向集合中**写入**（消费数据）→ 用 `super`

```java
// extends：只能读，不能写（除 null）
List<? extends Number> producers = new ArrayList<Integer>();
Number n = producers.get(0);  // ✅ 读出来是 Number
producers.add(1);             // ❌ 编译错误，类型不确定

// super：只能写，读出来是 Object
List<? super Integer> consumers = new ArrayList<Number>();
consumers.add(1);             // ✅ 可以写 Integer 或其子类
Object obj = consumers.get(0); // ✅ 读出来只知道是 Object

// 实际应用：Collections.copy 的签名
public static <T> void copy(
    List<? super T> dest,     // 消费者：只写
    List<? extends T> src     // 生产者：只读
) { ... }
```

### 3.3 无界通配符 vs 原始类型

```java
List<?>    list1 = new ArrayList<String>(); // 通配符：类型安全，不能写
List       list2 = new ArrayList<String>(); // 原始类型：完全绕过泛型检查

list1.add("a");  // ❌ 编译错误
list2.add("a");  // ✅ 但会有 unchecked warning
```

---

## 4. Type 体系

### 4.1 Type 接口层次结构

`java.lang.reflect.Type` 是所有类型的顶层接口，有以下五个子类型：

```
java.lang.reflect.Type
├── Class<T>                    —— 原始类型、基本类型、数组类型
├── ParameterizedType           —— 参数化类型：List<String>、Map<K,V>
├── TypeVariable<D>             —— 类型变量：T、E、K
├── WildcardType                —— 通配符：?、? extends T、? super T
└── GenericArrayType            —— 泛型数组：T[]、List<String>[]
```

### 4.2 ParameterizedType

表示带实际类型参数的参数化类型，如 `List<String>`、`Map<String, Integer>`。

```java
// 获取 ParameterizedType 的���式
class Container {
    List<String> items;
}

Field field = Container.class.getDeclaredField("items");
Type type = field.getGenericType();   // List<String>

if (type instanceof ParameterizedType pt) {
    Type raw = pt.getRawType();                  // class java.util.List
    Type[] args = pt.getActualTypeArguments();   // [class java.lang.String]
    Type owner = pt.getOwnerType();              // null（非内部类）
    
    System.out.println(raw);    // interface java.util.List
    System.out.println(args[0]); // class java.lang.String
}
```

### 4.3 TypeVariable

表示泛型声明中的类型参数本身（如 `T`、`E`），而非其实际类型。

```java
class Box<T extends Number & Comparable<T>> {
    T value;
}

// 获取类型变量
TypeVariable<?>[] typeParams = Box.class.getTypeParameters();
TypeVariable<?> T = typeParams[0];

System.out.println(T.getName());          // T
System.out.println(T.getBounds()[0]);     // class java.lang.Number
System.out.println(T.getBounds()[1]);     // java.lang.Comparable<T>
System.out.println(T.getGenericDeclaration()); // class Box
```

### 4.4 WildcardType

表示通配符类型：`?`、`? extends T`、`? super T`。

```java
class Container {
    List<? extends Number> uppers;    // 上界
    List<? super Integer>  lowers;    // 下界
}

Field f1 = Container.class.getDeclaredField("uppers");
ParameterizedType pt1 = (ParameterizedType) f1.getGenericType();
WildcardType wt1 = (WildcardType) pt1.getActualTypeArguments()[0];

System.out.println(wt1.getUpperBounds()[0]); // class java.lang.Number
System.out.println(wt1.getLowerBounds().length); // 0（无下界）

Field f2 = Container.class.getDeclaredField("lowers");
ParameterizedType pt2 = (ParameterizedType) f2.getGenericType();
WildcardType wt2 = (WildcardType) pt2.getActualTypeArguments()[0];

System.out.println(wt2.getLowerBounds()[0]); // class java.lang.Integer
System.out.println(wt2.getUpperBounds()[0]); // class java.lang.Object（隐式上界）
```

### 4.5 GenericArrayType

表示元素为参数化类型或类型变量的数组，如 `T[]`、`List<String>[]`。

```java
class Container<T> {
    T[]            arr1;  // GenericArrayType，元素是 TypeVariable
    List<String>[] arr2;  // GenericArrayType，元素是 ParameterizedType
    String[]       arr3;  // 普通 Class，不是 GenericArrayType
}

Field f = Container.class.getDeclaredField("arr1");
GenericArrayType gat = (GenericArrayType) f.getGenericType();
System.out.println(gat.getGenericComponentType()); // T（TypeVariable）
```

### 4.6 五种 Type 对比总结

| 类型 | 典型例子 | 关键方法 |
|------|----------|----------|
| `Class<T>` | `String`、`int[]`、`Object` | `getSuperclass()`、`getInterfaces()` |
| `ParameterizedType` | `List<String>`、`Map<K,V>` | `getRawType()`、`getActualTypeArguments()` |
| `TypeVariable` | `T`、`E`、`K` | `getName()`、`getBounds()`、`getGenericDeclaration()` |
| `WildcardType` | `?`、`? extends T`、`? super T` | `getUpperBounds()`、`getLowerBounds()` |
| `GenericArrayType` | `T[]`、`List<String>[]` | `getGenericComponentType()` |

---

## 5. Class 与泛型

### 5.1 Class<T> 的泛型参数含义

`Class<T>` 中的 `T` 表示该 Class 对象所代表的类型，用于编译期类型安全。

```java
Class<String>  strClass  = String.class;   // T = String
Class<Integer> intClass  = Integer.class;  // T = Integer
Class<?>       unknownClass = obj.getClass(); // 通配符，因为运行时类型未知

// 利用 Class<T> 实现类型安全的工厂方法
<T> T newInstance(Class<T> clazz) throws Exception {
    return clazz.getDeclaredConstructor().newInstance(); // 返回 T，无需强转
}

String s = newInstance(String.class);   // ✅ 类型安全
```

### 5.2 为什么 getClass() 返回 Class<?>

```java
Object obj = "hello";
Class<?> cls = obj.getClass();  // Class<? extends String>，编译器收窄为通配符
// 因为运行时类型可能是 String 的任意子类，编译器不知道确切类型
```

### 5.3 Class 作为类型令牌（Type Token）

将 `Class<T>` 作为参数传递，是绕过类型擦除的经典手段：

```java
// 类型安全的异构容器（Effective Java 条款33）
class TypeSafeMap {
    private Map<Class<?>, Object> map = new HashMap<>();

    public <T> void put(Class<T> type, T value) {
        map.put(type, value);
    }

    public <T> T get(Class<T> type) {
        return type.cast(map.get(type));  // cast() 比强转更安全
    }
}

TypeSafeMap container = new TypeSafeMap();
container.put(String.class, "hello");
container.put(Integer.class, 42);

String s = container.get(String.class);   // ✅ 无需强转
Integer i = container.get(Integer.class); // ✅ 类型安全
```

### 5.4 Class 的常用泛型相关方法

```java
Class<String> cls = String.class;

// 类型检查与转换
cls.cast(obj);           // 类型安全的强转，失败抛 ClassCastException
cls.isInstance(obj);     // 等价于 obj instanceof String

// 获取泛型信息（仅对有泛型的父类/接口有意义）
cls.getGenericSuperclass();      // 带泛型的父类 Type
cls.getGenericInterfaces();      // 带泛型的接口 Type[]

// 示例：获取 ArrayList 的泛型父类
class StringList extends ArrayList<String> {}
Type superType = StringList.class.getGenericSuperclass();
// superType = java.util.ArrayList<java.lang.String>  （ParameterizedType）
```

---

## 6. TypeToken 与泛型类型捕获

### 6.1 问题：无法直接获取 List<String>.class

```java
// ❌ 编译错误：不能对参数化类型取 .class
Class<?> cls = List<String>.class;

// ❌ 运行时只能得到 List，丢失了 String
Class<?> cls = new ArrayList<String>().getClass();  // class java.util.ArrayList
```

### 6.2 匿名子类技巧（Super Type Token）

利用**匿名子类**保留泛型信息，这是因为**子类的父类类型**（Generic Superclass）在字节码中会被保留：

```java
// 原理：匿名类继承时，父类的类型参数被记录在字节码中
Type type = new TypeReference<List<String>>(){}.getType();
// 通过 getGenericSuperclass() 可以拿到 TypeReference<List<String>>
// 再通过 getActualTypeArguments()[0] 得到 List<String>
```

### 6.3 自实现 TypeToken

```java
public abstract class TypeToken<T> {
    private final Type type;

    // 构造时通过 getGenericSuperclass() 捕获类型参数
    protected TypeToken() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType pt) {
            this.type = pt.getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("Missing type parameter");
        }
    }

    public Type getType() { return type; }
}

// 使用方式：通过匿名子类捕获泛型
TypeToken<List<String>> token = new TypeToken<List<String>>() {};
System.out.println(token.getType()); // java.util.List<java.lang.String>

TypeToken<Map<String, List<Integer>>> token2 
    = new TypeToken<Map<String, List<Integer>>>() {};
System.out.println(token2.getType()); 
// java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>
```

### 6.4 主流框架中的应用

| 框架 | 类名 | 典型用法 |
|------|------|----------|
| Gson | `TypeToken<T>` | `new TypeToken<List<User>>(){}.getType()` |
| Jackson | `TypeReference<T>` | `new TypeReference<List<User>>(){}` |
| Spring | `ParameterizedTypeReference<T>` | `new ParameterizedTypeReference<List<User>>(){}` |
| Guava | `TypeToken<T>` | `TypeToken.of(String.class)` |

```java
// Gson 反序列化 List<User>
Gson gson = new Gson();
Type type = new TypeToken<List<User>>(){}.getType();
List<User> users = gson.fromJson(json, type);

// Jackson 反序列化 List<User>
ObjectMapper mapper = new ObjectMapper();
List<User> users = mapper.readValue(json, new TypeReference<List<User>>(){});

// Spring RestTemplate 获取泛型响应
ResponseEntity<List<User>> resp = restTemplate.exchange(
    url, HttpMethod.GET, null,
    new ParameterizedTypeReference<List<User>>(){}
);
```

---

## 7. 泛型与反射

### 7.1 获取字段的泛型类型

```java
class Service {
    private List<String>         names;
    private Map<String, Integer> scores;
    private Optional<User>       currentUser;
}

// 遍历字段，提取泛型信息
for (Field field : Service.class.getDeclaredFields()) {
    Type type = field.getGenericType();
    System.out.print(field.getName() + " -> ");

    if (type instanceof ParameterizedType pt) {
        System.out.print("RawType=" + pt.getRawType().getTypeName());
        System.out.print(", Args=" + Arrays.toString(pt.getActualTypeArguments()));
    } else {
        System.out.print(type.getTypeName());
    }
    System.out.println();
}
// names  -> RawType=java.util.List,    Args=[class java.lang.String]
// scores -> RawType=java.util.Map,     Args=[class java.lang.String, class java.lang.Integer]
// currentUser -> RawType=java.util.Optional, Args=[class User]
```

### 7.2 获取方法的泛型参数和返回值

```java
class Repo<T> {
    public List<T> findAll() { ... }
    public <R> R convert(T source, Class<R> targetType) { ... }
}

Method findAll = Repo.class.getMethod("findAll");
Type returnType = findAll.getGenericReturnType();
// ParameterizedType: java.util.List<T>

Method convert = Repo.class.getMethod("convert", Object.class, Class.class);
Type[] paramTypes = convert.getGenericParameterTypes();
// [T, java.lang.Class<R>]

// 方法自身的类型参数
TypeVariable<?>[] methodTypeParams = convert.getTypeParameters();
// [R]
```

### 7.3 获取类的泛型父类和接口

```java
class StringRepo extends BaseRepo<String> implements Serializable, Comparable<String> {}

// 父类泛型
Type superclass = StringRepo.class.getGenericSuperclass();
// BaseRepo<java.lang.String>  （ParameterizedType）
ParameterizedType pt = (ParameterizedType) superclass;
System.out.println(pt.getActualTypeArguments()[0]); // class java.lang.String

// 接口泛型
Type[] interfaces = StringRepo.class.getGenericInterfaces();
// [interface java.io.Serializable, java.lang.Comparable<java.lang.String>]
```

---

## 8. 常见陷阱与最佳实践

### 8.1 泛型与数组不兼容

```java
// ❌ 不能创建泛型数组（编译错误）
List<String>[] arr = new List<String>[10];

// ✅ 用 List<List<String>> 代替
List<List<String>> list = new ArrayList<>();

// ✅ 若必须用数组，用原始类型并加 @SuppressWarnings
@SuppressWarnings("unchecked")
List<String>[] arr = new List[10];
```

### 8.2 堆污染（Heap Pollution）

```java
// 当参数化类型变量指向非同类参数化类型对象时发生堆污染
List<String> strings = new ArrayList<>();
List rawList = strings;    // 合法，原始类型赋值
rawList.add(42);           // 编译通过（仅 warning）

String s = strings.get(0); // 运行时 ClassCastException！（堆已被污染）

// 可变参数 + 泛型 = 堆污染风险
@SafeVarargs  // 确认实现安全后加此注解，抑制警告
public static <T> List<T> combine(List<T>... lists) { ... }
```

### 8.3 泛型方法 vs 通配符的选择

```java
// 方式一：泛型方法（当两个参数类型存在关联时使用）
<T> void copy(List<T> src, List<T> dst) { }

// 方式二：通配符（当类型参数只出现一次，且无关联约束时使用）
void print(List<?> list) { }

// 示例：swap 必须用泛型方法建立关联
<T> void swap(List<T> list, int i, int j) {
    T temp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, temp);
}
```

### 8.4 递归类型边界

```java
// 典型场景：实现 Comparable 的自引用约束
class Range<T extends Comparable<T>> {
    T min, max;
    boolean contains(T value) {
        return min.compareTo(value) <= 0 && value.compareTo(max) <= 0;
    }
}

// JDK 中的例子：Enum<E extends Enum<E>>
// 这保证了 ordinal() 等方法在子类中返回正确类型
enum Color { RED, GREEN, BLUE }  // 实际：class Color extends Enum<Color>
```

### 8.5 获取泛型信息的完整工具方法

```java
/**
 * 递归解析 Type，返回可读字符串（含嵌套泛型）
 */
public static String describeType(Type type) {
    if (type instanceof Class<?> cls) {
        return cls.getSimpleName();
    } else if (type instanceof ParameterizedType pt) {
        String raw = describeType(pt.getRawType());
        String args = Arrays.stream(pt.getActualTypeArguments())
                            .map(Utils::describeType)
                            .collect(Collectors.joining(", ", "<", ">"));
        return raw + args;
    } else if (type instanceof TypeVariable<?> tv) {
        return tv.getName();
    } else if (type instanceof WildcardType wt) {
        if (wt.getLowerBounds().length > 0)
            return "? super " + describeType(wt.getLowerBounds()[0]);
        if (wt.getUpperBounds().length > 0 && !wt.getUpperBounds()[0].equals(Object.class))
            return "? extends " + describeType(wt.getUpperBounds()[0]);
        return "?";
    } else if (type instanceof GenericArrayType gat) {
        return describeType(gat.getGenericComponentType()) + "[]";
    }
    return type.toString();
}

// 示例：
// describeType(new TypeToken<Map<String, List<Integer>>>(){}.getType())
// → "Map<String, List<Integer>>"
```

---

*后续相关提问将持续追加至文末。*

---

## Q&A

### Q1：泛型运行期擦除，反射如何还能获取到类型信息？

#### 核心区分：擦除的是"实例"，保留的是"声明"

类型擦除针对的是**运行时对象实例**，而不是**类文件中的声明元数据**。
Java 编译器在生成字节码时，会把泛型声明写入 `.class` 文件的 **`Signature` 属性**中，反射正是读取这个属性。

```
误解：泛型信息全部丢失
事实：
  - 运行时对象 new ArrayList<String>()  → 丢失，只知道是 ArrayList
  - 字段声明   List<String> names        → 保留，Signature 属性记录了 <String>
  - 方法签名   List<String> getAll()      → 保留，Signature 属性记录了 <String>
  - 类声明     class Foo extends Bar<String> → 保留，Signature 属性记录了 <String>
```

#### 用 javap 直接验证

```java
class Demo {
    List<String> names;
    List<String> getNames() { return names; }
}
```

执行 `javap -verbose Demo.class`，可以看到字节码中明确有 Signature 属性：

```
Field #1
  descriptor: Ljava/util/List;          ← 擦除后的描述符（运行时类型）
  Signature: Ljava/util/List<Ljava/lang/String;>;  ← 保留的泛型签名！

Method getNames
  descriptor: ()Ljava/util/List;
  Signature: ()Ljava/util/List<Ljava/lang/String;>;
```

两条信息并存：
- `descriptor` 是 JVM 运行时实际使用的（已擦除）
- `Signature` 是给反射 API 读取的（保留泛型）

#### 哪些地方会保留 Signature，哪些不会

| 位置 | 泛型信息是否保留 | 反射方法 |
|------|-----------------|----------|
| 字段声明 `List<String> f` | ✅ 保留 | `field.getGenericType()` |
| 方法参数 `void set(List<String> v)` | ✅ 保留 | `method.getGenericParameterTypes()` |
| 方法返回值 `List<String> get()` | ✅ 保留 | `method.getGenericReturnType()` |
| 类声明 `class Foo<T>` | ✅ 保留 | `cls.getTypeParameters()` |
| 父类声明 `extends Bar<String>` | ✅ 保留 | `cls.getGenericSuperclass()` |
| 接口声明 `implements Comparable<String>` | ✅ 保留 | `cls.getGenericInterfaces()` |
| 局部变量 `List<String> list = ...` | ❌ 擦除 | 无法获取 |
| `new ArrayList<String>()` 的实例 | ❌ 擦除 | 只能得到 `ArrayList` |

#### 这就是 TypeToken 匿名类技巧的本质

直接写 `new TypeToken<List<String>>(){}` 能拿到类型，原因就在这里：

```
步骤分解：

1. new TypeToken<List<String>>() {}
   ↓ 编译器生成一个匿名类，设为 $Anon
   
2. $Anon 继承自 TypeToken<List<String>>
   ↓ 这是一条【类声明】，编译器把它写入 $Anon.class 的 Signature 属性
   
3. $Anon.class 的字节码中：
   Signature: LTypeToken<Ljava/util/List<Ljava/lang/String;>;>;
   
4. 运行时：
   getClass()                    → $Anon（匿名类）
   .getGenericSuperclass()       → TypeToken<List<String>>（ParameterizedType）
   .getActualTypeArguments()[0]  → List<String>（我们想要的！）
```

代码验证：

```java
abstract class TypeToken<T> {
    protected TypeToken() {
        Type superClass = getClass().getGenericSuperclass();
        // superClass 是 ParameterizedType: TypeToken<List<String>>
        ParameterizedType pt = (ParameterizedType) superClass;
        Type captured = pt.getActualTypeArguments()[0];
        // captured = List<String>（完整类型，非擦除）
        System.out.println(captured);
    }
}

// 匿名类：$Anon extends TypeToken<List<String>>
// 父类声明 → 写入 Signature → 反射可读
new TypeToken<List<String>>() {};
// 输出：java.util.List<java.lang.String>
```

对比直接实例化为何失败：

```java
// ❌ 直接实例化：没有子类声明，没有 Signature，无法捕获
TypeToken<List<String>> t = new TypeToken<List<String>>();
// getGenericSuperclass() 返回的是 Object，拿不到 List<String>

// ✅ 匿名子类：有子类声明，Signature 保留了类型参数
TypeToken<List<String>> t = new TypeToken<List<String>>() {};
//                                                       ^^ 这对花括号生成了匿名子类
```

#### 总结

```
类型擦除 ≠ 泛型信息完全消失

JVM 运行时：不认识泛型，只看 descriptor（已擦除）
.class 文件：Signature 属性保存了完整的泛型声明，供反射读取

能读到泛型信息的前提：必须有一条显式的"声明"（字段/方法/类继承）
                      局部变量和 new 表达式没有声明，所以读不到
```

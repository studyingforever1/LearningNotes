import java.lang.reflect.*;
import java.util.*;

class User {}
class Order {}

class Example<T> {
    public User user;                      // Class
    public List<User> userList;            // ParameterizedType
    public Map<String, Order> orderMap;    // ParameterizedType
    public T[] genericArray;               // GenericArrayType
    public T typeVariable;                 // TypeVariable
    public List<? extends User> wildcard;  // WildcardType
}

public class TypeDemo {
    Example<Integer> example = new Example<>();

    public static void main(String[] args) throws NoSuchFieldException {
        Class<?> clazz = TypeDemo.class;

        // 1. 获取 example 字段
        Field exampleField = clazz.getDeclaredField("example");
        Type exampleType = exampleField.getGenericType();
        System.out.println("example field type: " + exampleType);

        // 判断 exampleType 是否是 ParameterizedType
        if (exampleType instanceof ParameterizedType pt) {
            Type actualTypeArg = pt.getActualTypeArguments()[0]; // T 对应的实际类型 Integer
            System.out.println("T 的实际类型: " + actualTypeArg);

            // 获取 Example<Integer> 内部字段
            Class<?> exampleClass = Example.class; // 泛型类
            for (Field f : exampleClass.getFields()) {
                Type fieldType = f.getGenericType();

                // 如果字段是 TypeVariable，需要替换成实际类型
                if (fieldType instanceof TypeVariable<?>) {
                    System.out.println(f.getName() + " : " + actualTypeArg);
                } else if (fieldType instanceof GenericArrayType gat) {
                    Type componentType = gat.getGenericComponentType();
                    if (componentType instanceof TypeVariable<?>) {
                        System.out.println(f.getName() + " : " + actualTypeArg + "[]");
                    } else {
                        System.out.println(f.getName() + " : " + fieldType);
                    }
                } else if (fieldType instanceof ParameterizedType p) {
                    System.out.print(f.getName() + " : " + p.getRawType() + "<");
                    Type[] argsArr = p.getActualTypeArguments();
                    for (int i = 0; i < argsArr.length; i++) {
                        if (argsArr[i] instanceof TypeVariable<?>) {
                            System.out.print(actualTypeArg.getTypeName());
                        } else if (argsArr[i] instanceof WildcardType wt) {
                            System.out.print("?");
                            Type[] upper = wt.getUpperBounds();
                            if (upper.length > 0) {
                                System.out.print(" extends " + upper[0].getTypeName());
                            }
                        } else {
                            System.out.print(argsArr[i].getTypeName());
                        }
                        if (i < argsArr.length - 1) System.out.print(", ");
                    }
                    System.out.println(">");
                } else {
                    System.out.println(f.getName() + " : " + fieldType.getTypeName());
                }
            }
        }
    }
}

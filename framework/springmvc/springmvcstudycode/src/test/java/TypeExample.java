import java.lang.reflect.*;
import java.util.*;

public class TypeExample {

    // 泛型类示例
    static class GenericClass<T> {
        List<String> stringList;           // ParameterizedType
        T[] genericArray;                  // GenericArrayType
        T genericField;                    // TypeVariable
        List<? extends Number> wildcardList; // WildcardType
    }

    private static GenericClass<Integer> genericClass;

    public static void main(String[] args) throws Exception {
        // 获取 GenericClass 的 Class 对象
        Class<?> clazz = TypeExample.class.getDeclaredField("genericClass").getType();
        System.out.println("Class 类型: " + clazz);

        // 1. ParameterizedType 示例
        Field stringListField = clazz.getDeclaredField("stringList");
        Type stringListType = stringListField.getGenericType();
        if (stringListType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) stringListType;
            System.out.println("ParameterizedType 类型: " + pt);
            System.out.println("  原始类型: " + pt.getRawType());
            System.out.println("  实际类型参数: " + Arrays.toString(pt.getActualTypeArguments()));
        }

        // 2. GenericArrayType 示例
        Field genericArrayField = clazz.getDeclaredField("genericArray");
        Type genericArrayType = genericArrayField.getGenericType();
        if (genericArrayType instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) genericArrayType;
            System.out.println("GenericArrayType 类型: " + gat);
            System.out.println("  数组的组件类型: " + gat.getGenericComponentType());
        }

        // 3. TypeVariable 示例
        Field genericField = clazz.getDeclaredField("genericField");
        Type genericFieldType = genericField.getGenericType();
        if (genericFieldType instanceof TypeVariable) {
            TypeVariable<?> tv = (TypeVariable<?>) genericFieldType;
            System.out.println("TypeVariable 类型: " + tv);
            System.out.println("  名称: " + tv.getName());
            System.out.println("  泛型声明者: " + tv.getGenericDeclaration());
        }

        // 4. WildcardType 示例
        Field wildcardListField = clazz.getDeclaredField("wildcardList");
        Type wildcardListType = wildcardListField.getGenericType();
        if (wildcardListType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) wildcardListType;
            Type[] typeArgs = pt.getActualTypeArguments();
            for (Type typeArg : typeArgs) {
                if (typeArg instanceof WildcardType) {
                    WildcardType wt = (WildcardType) typeArg;
                    System.out.println("WildcardType 类型: " + wt);
                    System.out.println("  上界: " + Arrays.toString(wt.getUpperBounds()));
                    System.out.println("  下界: " + Arrays.toString(wt.getLowerBounds()));
                }
            }
        }
    }
}

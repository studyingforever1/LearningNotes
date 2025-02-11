package com.zcq.demo.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class AsmTest {

    public static void main(String[] args) throws Exception {
        // 加载Class字节码
        ClassReader cr = new ClassReader(AsmTest.class.getName());
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(classWriter, 0);
        // 获取并输出修改后的字节码
        byte[] bytecode = classWriter.toByteArray();
        System.out.println(bytecode);
    }
}

package com.neoclar.jbi.java;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.ASM9;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;

public class ClassCreator extends ClassVisitor {
    HashMap<ArrayList<String>, MethodVisitor> modifiedMethods = new HashMap<ArrayList<String>, MethodVisitor>();
    ClassVisitor classVisitor;
    Set<String> classInterfaces;
    ArrayList<ArrayList<String>> delMethods;
    public ClassCreator(ClassVisitor classVisitor, HashMap<ArrayList<String>, MethodVisitor> modifiedMethods, Set<String> interfaces, ArrayList<ArrayList<String>> delMethods) { 
        super(ASM9, classVisitor);
        this.modifiedMethods = modifiedMethods;
        this.classVisitor = classVisitor;
        this.classInterfaces = interfaces;
        this.delMethods = delMethods;
    }
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, (classInterfaces).toArray(new String[]{}));
    }

    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        ArrayList<String> methodInfo = new ArrayList<String>(){};
        methodInfo.add(name);
        methodInfo.add(""+access);
        methodInfo.add(descriptor);
        methodInfo.add(signature);
        if (delMethods.contains(methodInfo)) {
            return null;
        }
        if (modifiedMethods.containsKey(methodInfo)) {
            return null;
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}
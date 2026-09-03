package com.fracturedzen.mcmessenger.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/** Rewrites unqualified hostnames in {@link java.net.InetAddress} lookups. */
public final class HostMapTransformer implements ClassFileTransformer, Opcodes {
    private static final String TARGET = "java/net/InetAddress";
    private static final String MAP = "com/fracturedzen/mcmessenger/agent/HostMap";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !TARGET.equals(className)) return null;
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    boolean byName = "getByName".equals(name)
                            && "(Ljava/lang/String;)Ljava/net/InetAddress;".equals(descriptor);
                    boolean allByName = "getAllByName".equals(name)
                            && "(Ljava/lang/String;)[Ljava/net/InetAddress;".equals(descriptor);
                    if (!byName && !allByName) return mv;
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKESTATIC, MAP, "map",
                                    "(Ljava/lang/String;)Ljava/lang/String;", false);
                            mv.visitVarInsn(ASTORE, 0);
                        }
                    };
                }
            }, 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }
}

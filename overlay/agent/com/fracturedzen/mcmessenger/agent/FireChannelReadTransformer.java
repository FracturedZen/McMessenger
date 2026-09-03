package com.fracturedzen.mcmessenger.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Patches Netty's inbound fireChannelRead (stable library names, not Minecraft obfuscation).
 */
public final class FireChannelReadTransformer implements ClassFileTransformer, Opcodes {
    private static final String TARGET = "io/netty/channel/AbstractChannelHandlerContext";
    private static final String DROPPER = "com/fracturedzen/mcmessenger/agent/PlayDropper";

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
                    if (!"fireChannelRead".equals(name) || !descriptor.startsWith("(Ljava/lang/Object;)")) {
                        return mv;
                    }
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKESTATIC, DROPPER, "noteContext", "(Ljava/lang/Object;)V", false);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitMethodInsn(INVOKESTATIC, DROPPER, "shouldDrop", "(Ljava/lang/Object;)Z", false);
                            Label cont = new Label();
                            mv.visitJumpInsn(IFEQ, cont);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitMethodInsn(INVOKESTATIC, DROPPER, "release", "(Ljava/lang/Object;)V", false);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitInsn(ARETURN);
                            mv.visitLabel(cont);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
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

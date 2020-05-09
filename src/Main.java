import com.sun.org.apache.bcel.internal.generic.CASTORE;
import com.sun.org.apache.bcel.internal.generic.ICONST;
import com.sun.org.apache.bcel.internal.generic.ILOAD;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Stack;

public class Main {
    private static boolean methods = false;
    private static boolean optimize = true;
    private static boolean run = false;
    public static void main(String[] args) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        String filename = null;
        String outputFilename = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("methods")) {
                methods = true;
            } else if (args[i].equals("!methods")) {
                methods = false;
            } else if (args[i].equals("optimize")) {
                optimize = true;
            } else if (args[i].equals("!optimize")) {
                optimize = false;
            } else if (args[i].equals("run")) {
                run = true;
            } else if (args[i].equals("!run")) {
                run = false;
            } else {
                if (filename == null) {
                    filename = args[i];
                } else if (outputFilename == null) {
                    outputFilename = args[i];
                } else {
                    System.err.println("Error: too much filenames");
                    return;
                }
            }
        }

        if (filename == null) {
            System.err.println("A filename should be provided");
            return;
        }

        System.out.println("Methods      " + (methods ? "ON" : "OFF"));
        System.out.println("Optimization " + (optimize ? "ON" : "OFF"));
        System.out.println("Run          " + (run ? "ON" : "OFF"));
        if (!run && outputFilename == null) System.err.println("Run is off and filename is not specified; nothing will be done");

        String code = new String(Files.readAllBytes(Paths.get(filename)));
        long startTime = System.nanoTime();

        byte[] arr;
        if (code.indexOf(',') == -1 && optimize) {
            arr = jitCompile(code, "", 0, null, 0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream nativeOut = System.out;
            System.setOut(new PrintStream(out));
            new ByteCodeLoader().loadClass(arr).getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[0]);
            System.setOut(nativeOut);
            String output = new String(out.toByteArray());
            arr = jitCompile(code, output, code.length(), null, 0);
        } else {
            PreExecutor executor = new PreExecutor(code);
            if (optimize) executor.execute();
            int start = executor.getIndex();
            String output = executor.getOutput().toString();


            arr = jitCompile(code, output, start, executor.getCells(), executor.getPtr());
        }
        ByteCodeLoader loader = new ByteCodeLoader();
        Class clazz = loader.loadClass(arr);

        long beginTime = System.nanoTime();
        long compileEndTime = System.nanoTime();

        if (run) clazz.getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[0]);

        if (run) System.out.println();
        System.out.println("Compile time: " + (compileEndTime - startTime) / 1000000 + "ms");
        if (run) System.out.println("Run time: " + (System.nanoTime() - beginTime) / 1000000 + "ms");
        if (outputFilename != null) Files.write(Paths.get(outputFilename), arr);
    }

    private static byte[] jitCompile(String code, String output, int start, char[] cells, int ptr) {
        ClassNode cn = new ClassNode();

        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER;
        cn.name = "BfJit";
        cn.superName = "java/lang/Object";

        {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            InsnList il = mn.instructions;

            if (optimize && output.length() > 0) {
                il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                il.add(new LdcInsnNode(output));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false));
            }

            if (!optimize || start < code.length()) {

                if (methods) {
                    {
                        FieldNode arrField = new FieldNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "cells", "[C", null, null);
                        cn.fields.add(arrField);
                    }
                    {
                        FieldNode indexField = new FieldNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "index", "I", null, null);
                        cn.fields.add(indexField);
                    }
                    {
                        MethodNode shift = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "shift", "(I)V", null, null);
                        shift.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "index", "I"));
                        shift.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
                        shift.instructions.add(new InsnNode(Opcodes.IADD));
                        shift.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, "BfJit", "index", "I"));
                        shift.instructions.add(new InsnNode(Opcodes.RETURN));
                        cn.methods.add(shift);
                    }
                    {
                        MethodNode plus = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "plus", "(I)V", null, null);
                        plus.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "cells", "[C"));
                        plus.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "index", "I"));
                        plus.instructions.add(new InsnNode(Opcodes.DUP2));
                        plus.instructions.add(new InsnNode(Opcodes.CALOAD));
                        plus.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
                        plus.instructions.add(new InsnNode(Opcodes.IADD));
                        plus.instructions.add(new InsnNode(Opcodes.CASTORE));
                        plus.instructions.add(new InsnNode(Opcodes.RETURN));
                        cn.methods.add(plus);
                    }
                    {
                        MethodNode getChar = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "getChar", "()C", null, null);
                        getChar.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "cells", "[C"));
                        getChar.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "index", "I"));
                        getChar.instructions.add(new InsnNode(Opcodes.CALOAD));
                        getChar.instructions.add(new InsnNode(Opcodes.IRETURN));
                        cn.methods.add(getChar);
                    }
                    {
                        MethodNode print = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "print", "()V", null, null);
                        print.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                        print.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "cells", "[C"));
                        print.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "index", "I"));
                        print.instructions.add(new InsnNode(Opcodes.CALOAD));
                        print.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(C)V", false));
                        print.instructions.add(new InsnNode(Opcodes.RETURN));
                        cn.methods.add(print);
                    }
                    {
                        MethodNode read = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "read", "()V", null, null);
                        read.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "cells", "[C"));
                        read.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "index", "I"));
                        read.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;"));
                        read.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "()I", false));
                        read.instructions.add(new InsnNode(Opcodes.CASTORE));
                        read.instructions.add(new InsnNode(Opcodes.RETURN));
                        cn.methods.add(read);
                    }
                    {
                        MethodNode zero = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "zero", "()V", null, null);
                        zero.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "cells", "[C"));
                        zero.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "BfJit", "index", "I"));
                        zero.instructions.add(new InsnNode(Opcodes.ICONST_0));
                        zero.instructions.add(new InsnNode(Opcodes.CASTORE));
                        zero.instructions.add(new InsnNode(Opcodes.RETURN));
                        cn.methods.add(zero);
                    }
                }

                il.add(new LdcInsnNode(10000));
                il.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR));
                if (cells != null) {
                    for (int i = 0; i < cells.length; i++) {
                        if (cells[i] != 0) {
                            il.add(new InsnNode(Opcodes.DUP));
                            il.add(new LdcInsnNode(i));
                            il.add(new LdcInsnNode(cells[i]));
                            il.add(new InsnNode(Opcodes.CASTORE));
                        }
                    }
                }
                if (methods) {
                    il.add(new FieldInsnNode(Opcodes.PUTSTATIC, "BfJit", "cells", "[C"));
                } else {
                    il.add(new VarInsnNode(Opcodes.ASTORE, 1));
                }
                if (cells != null) {
                    il.add(new LdcInsnNode(ptr));
                } else {
                    il.add(new InsnNode(Opcodes.ICONST_0));
                }
                if (methods) {
                    il.add(new FieldInsnNode(Opcodes.PUTSTATIC, "BfJit", "index", "I"));
                } else {
                    il.add(new VarInsnNode(Opcodes.ISTORE, 2));
                }

                Stack<LabelNode> labels = new Stack<>();

                for (int i = start; i < code.length(); i++) {
                    if (code.charAt(i) == '+' || code.charAt(i) == '-') {
                        int count = 0;
                        while (i < code.length() && (code.charAt(i) == '+' || code.charAt(i) == '-')) {
                            count += code.charAt(i) == '+' ? 1 : -1;
                            i++;
                        }
                        i--;
                        if (count != 0) {
                            if (methods) {
                                if (count > 0 && count <= 5) {
                                    il.add(new InsnNode(Opcodes.ICONST_0 + count));
                                } else {
                                    il.add(new LdcInsnNode(count));
                                }
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "BfJit", "plus", "(I)V", false));
                            } else {
                                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                                il.add(new InsnNode(Opcodes.DUP2));
                                il.add(new InsnNode(Opcodes.CALOAD));
                                if (count > 0 && count <= 5) {
                                    il.add(new InsnNode(Opcodes.ICONST_0 + count));
                                } else {
                                    il.add(new LdcInsnNode(count));
                                }
                                il.add(new InsnNode(Opcodes.IADD));
                                il.add(new InsnNode(Opcodes.CASTORE));
                            }
                        }
                    } else if (code.charAt(i) == '>' || code.charAt(i) == '<') {
                        int count = 0;
                        while (i < code.length() && (code.charAt(i) == '>' || code.charAt(i) == '<')) {
                            count += code.charAt(i) == '>' ? 1 : -1;
                            i++;
                        }
                        i--;
                        if (count != 0) {
                            if (count > 0 && count <= 5) {
                                il.add(new InsnNode(Opcodes.ICONST_0 + count));
                            } else {
                                il.add(new LdcInsnNode(count));
                            }
                            if (methods) {
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "BfJit", "shift", "(I)V", false));
                            } else {
                                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                                il.add(new InsnNode(Opcodes.IADD));
                                il.add(new VarInsnNode(Opcodes.ISTORE, 2));
                            }
                        }
                    } else if (code.charAt(i) == '.') {
                        if (methods) {
                            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "BfJit", "print", "()V", false));
                        } else {
                            il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                            il.add(new InsnNode(Opcodes.CALOAD));
                            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(C)V", false));
                        }
                    } else if (code.charAt(i) == ',') {
                        if (methods) {
                            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "BfJit", "read", "()V", false));
                        } else {
                            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                            il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;"));
                            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "()I", false));
                            il.add(new InsnNode(Opcodes.CASTORE));
                        }
                    } else if (code.charAt(i) == '[') {
                        if (i + 2 < code.length() && (code.charAt(i + 1) == '-' || code.charAt(i + 1) == '+') && code.charAt(i + 2) == ']') {
                            i += 2;
                            if (methods) {
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "BfJit", "zero", "()V", false));
                            } else {
                                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                                il.add(new InsnNode(Opcodes.ICONST_0));
                                il.add(new InsnNode(Opcodes.CASTORE));
                            }
                        } else {
                            LabelNode begin = new LabelNode(), end = new LabelNode();
                            labels.push(begin);
                            labels.push(end);
                            il.add(begin);
                            if (methods) {
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "BfJit", "getChar", "()C", false));
                            } else {
                                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                                il.add(new InsnNode(Opcodes.CALOAD));
                            }
                            il.add(new JumpInsnNode(Opcodes.IFEQ, end));
                        }
                    } else if (code.charAt(i) == ']') {
                        LabelNode end = labels.pop(), begin = labels.pop();
                        il.add(new JumpInsnNode(Opcodes.GOTO, begin));
                        il.add(end);
                    }
                }
            }

            il.add(new InsnNode(Opcodes.RETURN));

            cn.methods.add(mn);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        cn.accept(cw);
        return cw.toByteArray();
    }
}

public class ByteCodeLoader extends ClassLoader {
    public Class<?> loadClass(byte[] bytecode) {
        return defineClass(null, bytecode, 0, bytecode.length);
    }
}

/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class GenericFactory {

    private GenericFactory(){}

    public static Class<?> getClass(String classname) throws ClassNotFoundException {
        if(classname == null){
            throw new NullPointerException();
        }

        Class<?> result = getPrimitiveClassFromPrimitiveName(classname);
        if(result == null){
            result = Class.forName(classname);
        }
        assert(result != null);
        return result;
    }

    // Note, in case of primitive target class, method always returns object of wrapper class
    public static Object smartCast(Class<?> targetClass, Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        Class<?> wrapperClass = targetClass;
        if(isPrimitiveClass(targetClass.getName())){
            wrapperClass = getWrapperClassFromPrimitiveName(targetClass.getName());
            assert(wrapperClass != null);
        }

        if (wrapperClass.isInstance(obj)) {
            return wrapperClass.cast(obj);
        } else if (isBaseType(wrapperClass.getName()) && isBaseType(obj.getClass().getName())) {
            assert (!isPrimitiveClass(obj.getClass().getName()) &&
                    isWrapperClass(obj.getClass().getName()) &&
                    !isPrimitiveClass(wrapperClass.getName()) &&
                    isWrapperClass(wrapperClass.getName()));

            if(isFloatType(obj)){
                double dObj = floatTypeWrapperToDouble(obj);

                if(wrapperClass.equals(Boolean.class)){
                    if(dObj == 1.0){
                        return Boolean.TRUE;
                    }
                    else if(dObj == 0.0){
                        return Boolean.FALSE;
                    }
                }
                else if(wrapperClass.equals(Character.class)){
                    if(Double.isFinite(dObj)){
                        char cObj = (char) dObj;
                        if(cObj == dObj){
                            return cObj;
                        }
                    }
                }
                else if(wrapperClass.equals(Byte.class)){
                    if(Double.isFinite(dObj)){
                        byte bObj = (byte) dObj;
                        if(bObj == dObj){
                            return bObj;
                        }
                    }
                }
                else if(wrapperClass.equals(Short.class)){
                    if(Double.isFinite(dObj)){
                        short  sObj = (short) dObj;
                        if(sObj == dObj){
                            return sObj;
                        }
                    }
                }
                else if(wrapperClass.equals(Integer.class)){
                    if(Double.isFinite(dObj)){
                        int  iObj = (int) dObj;
                        if(iObj == dObj){
                            return iObj;
                        }
                    }
                }
                else if(wrapperClass.equals(Long.class)){
                    if(Double.isFinite(dObj)){
                        long  lObj = (long) dObj;
                        if(lObj == dObj){
                            return lObj;
                        }
                    }
                }
                else if(wrapperClass.equals(Float.class)){
                    if(Double.isNaN(dObj)){
                        return Float.NaN;
                    }
                    else if(Double.isInfinite(dObj)){
                        if(dObj > 0){
                            assert(dObj == Double.POSITIVE_INFINITY);
                            return Float.POSITIVE_INFINITY;
                        }
                        else{
                            assert(dObj == Double.NEGATIVE_INFINITY);
                            return Float.NEGATIVE_INFINITY;
                        }
                    }
                    else {
                        float fObj = (float) dObj;
                        if(fObj == dObj){
                            return fObj;
                        }
                    }
                }
                else {
                    assert(wrapperClass.equals(Double.class));

                    return dObj;
                }
            }
            else{
                assert(isIntType(obj));
                long lObj = intTypeWrapperToLong(obj);

                if(wrapperClass.equals(Boolean.class)){
                    if(lObj == 1){
                        return Boolean.TRUE;
                    }
                    else if(lObj == 0){
                        return Boolean.FALSE;
                    }
                }
                else if(wrapperClass.equals(Character.class)){
                    char cObj = (char) lObj;
                    if(cObj == lObj){
                        return cObj;
                    }
                }
                else if(wrapperClass.equals(Byte.class)){
                    byte bObj = (byte) lObj;
                    if(bObj == lObj){
                        return bObj;
                    }
                }
                else if(wrapperClass.equals(Short.class)){
                    short sObj = (short) lObj;
                    if(sObj == lObj){
                        return sObj;
                    }
                }
                else if(wrapperClass.equals(Integer.class)){
                    int iObj = (int) lObj;
                    if(iObj == lObj){
                        return iObj;
                    }
                }
                else if(wrapperClass.equals(Long.class)){
                    return lObj;
                }
                else if(wrapperClass.equals(Float.class)){
                    float fObj = (float) lObj;
                    if(fObj == lObj){
                        return fObj;
                    }
                }
                else {
                    assert(wrapperClass.equals(Double.class));

                    double dObj = (double) lObj;
                    if(dObj == lObj){
                        return dObj;
                    }
                }
            }

            throw new ClassCastException("No cast possible without information loss.");
        } else {
            throw new ClassCastException("No cast possible.");
        }

    }

    public static boolean isFloatType(Class<?> cls){
        if(cls == null){
            throw new NullPointerException();
        }
        return cls.equals(Float.class) ||
                cls.equals(Double.class);
    }

    public static boolean isFloatType(Object obj){
        if(obj == null){
            throw new NullPointerException();
        }
        return isFloatType(obj.getClass());
    }

    public static double floatTypeWrapperToDouble(Object obj){
        if(obj == null){
            throw new NullPointerException();
        }
        Class<?> objCls = obj.getClass();
        if(objCls.equals(Float.class)){
            return (Float) obj;
        }
        else if(objCls.equals(Double.class)){
            return (Double) obj;
        }
        else{
            throw new IllegalArgumentException("'obj' does not have the expected type.");
        }
    }

    public static boolean isIntType(Class<?> cls){
        if(cls == null){
            throw new NullPointerException();
        }
        return cls.equals(Boolean.class) ||
                cls.equals(Character.class) ||
                cls.equals(Byte.class) ||
                cls.equals(Short.class) ||
                cls.equals(Integer.class) ||
                cls.equals(Long.class);
    }

    public static boolean isIntType(Object obj){
        if(obj == null){
            throw new NullPointerException();
        }
        return isIntType(obj.getClass());
    }

    public static long intTypeWrapperToLong(Object obj){
        if(obj == null){
            throw new NullPointerException();
        }
        Class<?> objCls = obj.getClass();
        if(objCls.equals(Boolean.class)){
            if((Boolean) obj){
                return 1;
            }
            else{
                return 0;
            }
        }
        else if(objCls.equals(Character.class)){
            return (Character) obj;
        }
        else if(objCls.equals(Byte.class)){
            return (Byte) obj;
        }
        else if(objCls.equals(Short.class)){
            return (Short) obj;
        }
        else if(objCls.equals(Integer.class)){
            return (Integer) obj;
        }
        else if(objCls.equals(Long.class)){
            return (Long) obj;
        }
        else{
            throw new IllegalArgumentException("'obj' does not have an expected type.");
        }
    }

    public static boolean isPrimitiveClass(String classname){
        return getPrimitiveClassFromPrimitiveName(classname) != null;
    }

    public static boolean isWrapperClass(String classname){
        return getPrimitiveClassFromWrapperName(classname) != null;
    }

    public static boolean isBaseType(String classname){
        return isPrimitiveClass(classname) || isWrapperClass(classname);
    }

    public static Class<?> getPrimitiveClassFromPrimitiveName(String classname){
        if(classname == null){
            throw new NullPointerException();
        }
        else if(classname.equals(boolean.class.getName())){
            return boolean.class;
        }
        else if(classname.equals(byte.class.getName())){
            return byte.class;
        }
        else if(classname.equals(short.class.getName())){
            return short.class;
        }
        else if(classname.equals(int.class.getName())){
            return int.class;
        }
        else if(classname.equals(long.class.getName())){
            return long.class;
        }
        else if(classname.equals(char.class.getName())){
            return char.class;
        }
        else if(classname.equals(float.class.getName())){
            return float.class;
        }
        else if(classname.equals(double.class.getName())){
            return double.class;
        }
        else{
            return null;
        }
    }

    public static Class<?> getWrapperClassFromPrimitiveName(String classname){
        if(classname == null){
            throw new NullPointerException();
        }
        else if(classname.equals(boolean.class.getName())){
            return Boolean.class;
        }
        else if(classname.equals(byte.class.getName())){
            return Byte.class;
        }
        else if(classname.equals(short.class.getName())){
            return Short.class;
        }
        else if(classname.equals(int.class.getName())){
            return Integer.class;
        }
        else if(classname.equals(long.class.getName())){
            return Long.class;
        }
        else if(classname.equals(char.class.getName())){
            return Character.class;
        }
        else if(classname.equals(float.class.getName())){
            return Float.class;
        }
        else if(classname.equals(double.class.getName())){
            return Double.class;
        }
        else{
            return null;
        }
    }

    public static Class<?> getPrimitiveClassFromWrapperName(String classname){
        if(classname == null){
            throw new NullPointerException();
        }
        else if(classname.equals(Boolean.class.getName())){
            return boolean.class;
        }
        else if(classname.equals(Byte.class.getName())){
            return byte.class;
        }
        else if(classname.equals(Short.class.getName())){
            return short.class;
        }
        else if(classname.equals(Integer.class.getName())){
            return int.class;
        }
        else if(classname.equals(Long.class.getName())){
            return long.class;
        }
        else if(classname.equals(Character.class.getName())){
            return char.class;
        }
        else if(classname.equals(Float.class.getName())){
            return float.class;
        }
        else if(classname.equals(Double.class.getName())){
            return double.class;
        }
        else{
            return null;
        }
    }

    private static Class<?>[] getClassesfromNames(String[] classnames) throws ClassNotFoundException {
        if(classnames == null){
            throw new NullPointerException();
        }

        Class<?>[] result = new Class[classnames.length];
        for(int i = 0; i < classnames.length; i++){
            result[i] = getPrimitiveClassFromPrimitiveName(classnames[i]);
            if(result[i] == null){ // No primitive class
                result[i] = Class.forName(classnames[i]);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static<T> T createFromExecutable(Object[] arguments, Executable exec){
        if(arguments == null){
            arguments = new Object[0];
        }

        if(exec == null){
            throw new NullPointerException();
        }

        T result;
        if(exec instanceof Constructor){
            Constructor<T> constructor;
            try {
                constructor = (Constructor<T>) exec;
                result = constructor.newInstance(arguments);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
            return result;
        }
        else if(exec instanceof Method){
            if(!Modifier.isStatic(exec.getModifiers())){
                throw new IllegalArgumentException();
            }

            try{
                result = (T) ((Method) exec).invoke(null, arguments);
            }
            catch (Exception e){
                throw new IllegalArgumentException(e);
            }
            return result;
        }
        else{
            throw new IllegalArgumentException("'exec' must be either either extend Constructor or Method");
        }
    }

    // classname may not be a primitive type!
    @SuppressWarnings("unchecked")
    public static<T> T createObject(String classname, String[] argumentClassnames, Object[] arguments) {
        if(classname == null){
            throw new NullPointerException();
        }
        else if(getPrimitiveClassFromPrimitiveName(classname) != null){
            throw new IllegalArgumentException("Classname represents a primitive class: "+classname);
        }

        if(argumentClassnames == null){
            argumentClassnames = new String[0];
        }

        Class<T> objectClass; // May throw a ClassCastException here
        try {
            objectClass = (Class<T>) Class.forName(classname);
            return createObject(objectClass, getClassesfromNames(argumentClassnames), arguments);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static<T> T createObject(Class<T> objectClass, Class<?>[] argumentClasses, Object[] arguments) {
        if(objectClass == null){
            throw new NullPointerException();
        }

        if(argumentClasses == null){
            argumentClasses = new Class[0];
        }
        if(arguments == null){
            arguments = new Object[0];
        }

        if(argumentClasses.length != arguments.length){
            throw new IllegalArgumentException();
        }

        Constructor<T> constructor;
        T result;
        try {
            constructor = objectClass.getDeclaredConstructor(argumentClasses);
            result = createFromExecutable(arguments, constructor);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return result;
    }

    public static<T> T createObject(String method_classname, String static_methodname, String[] argumentClassnames, Object[] arguments) {
        if(method_classname == null){
            throw new NullPointerException();
        }

        if(static_methodname == null){
            throw new NullPointerException();
        }

        if(argumentClassnames == null){
            argumentClassnames = new String[0];
        }

        Class<?> methodClass; // May throw a ClassCastException here
        try {
            methodClass = Class.forName(method_classname);
            return createObject(methodClass, static_methodname, getClassesfromNames(argumentClassnames), arguments);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static<T> T createObject(Class<?> methodClass, String static_methodname, Class<?>[] argumentClasses, Object[] arguments) {
        if(methodClass == null){
            throw new NullPointerException();
        }

        if(argumentClasses == null){
            argumentClasses = new Class[0];
        }
        if(arguments == null){
            arguments = new Object[0];
        }

        if(argumentClasses.length != arguments.length){
            throw new IllegalArgumentException();
        }

        Method method;
        try{
            method = methodClass.getMethod(static_methodname, argumentClasses);
            return createFromExecutable(arguments, method);
        }
        catch(Exception e){
            throw new IllegalArgumentException(e);
        }
    }
}

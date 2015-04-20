package com.youngsee.common;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {
	public static Object getProperty(Object owner, String fieldName) {
		try {
		    Field field = owner.getClass().getField(fieldName);
		    field.setAccessible(true);
			return field.get(owner);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	    
	    return null;
	}
	
	public static Object getStaticProperty(String className, String fieldName) {  
		try {
			Class<?> ownerClass = Class.forName(className);
			Field field = ownerClass.getField(fieldName);
			field.setAccessible(true);
	        return field.get(ownerClass); 
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}  
  
        return null;
    } 
	
	public static Object invokeMethod(Object owner, String methodName, Object[] args) {  
		try {
			Class<?>[] argsClass = new Class[args.length];
			for (int i = 0, j = args.length; i < j; i++) {
				argsClass[i] = args[i].getClass();
			}
	        Method method = owner.getClass().getMethod(methodName, argsClass);
	        method.setAccessible(true);
			return method.invoke(owner, args); 
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}  
  
        return null;
    }
	
	public static Object invokeMethod(Object owner, String methodName,
			Object[] args, Class<?>[] argsClass) {
		if (args.length != argsClass.length) {
			return null;
		}
		try {
	        Method method = owner.getClass().getMethod(methodName, argsClass);
	        method.setAccessible(true);
			return method.invoke(owner, args); 
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}  
  
        return null;
    }
	
	public static Object invokeStaticMethod(String className, String methodName, Object[] args) {  
		try {
			Class<?>[] argsClass = new Class[args.length];
	        for (int i = 0, j = args.length; i < j; i++) {
	        	argsClass[i] = args[i].getClass();
	        }
	        Method method = Class.forName(className).getMethod(methodName, argsClass);
	        method.setAccessible(true);
	        return method.invoke(null, args);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}  
  
        return null;
    }
	
	public static Object invokeStaticMethod(String className, String methodName,
			Object[] args, Class<?>[] argsClass) {
		if (args.length != argsClass.length) {
			return null;
		}
		try {
	        Method method = Class.forName(className).getMethod(methodName, argsClass);
	        method.setAccessible(true);
	        return method.invoke(null, args);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}  
  
        return null;
    }
	
	public static Object newInstance(String className, Object[] args) {
		try {
			Class<?>[] argsClass = new Class[args.length];
	        for (int i = 0, j = args.length; i < j; i++) {
	            argsClass[i] = args[i].getClass();
	        }
			return Class.forName(className).getConstructor(argsClass).newInstance(args);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
          
		return null;
    }
	
	public static Object newInstance(String className,
			Object[] args, Class<?>[] argsClass) {
		if (args.length != argsClass.length) {
			return null;
		}
		try {
			return Class.forName(className).getConstructor(argsClass).newInstance(args);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
          
		return null;
    }
	
	public static boolean isInstance(Object obj, Class<?> cls) {  
		return cls.isInstance(obj);
    }
	
	public static Object getByArray(Object array, int index) {
		return Array.get(array, index);
	}
}

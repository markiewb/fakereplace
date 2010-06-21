package org.fakereplace.data;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.fakereplace.util.DescriptorUtils;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;

/**
 * This class holds everything there is to know about a class that has been seen
 * by the transformer
 * 
 * @author stuart
 * 
 */
public class ClassData
{

   private final String className;
   private final String internalName;
   private final Map<String, Map<String, Set<MethodData>>> methods = new ConcurrentHashMap<String, Map<String, Set<MethodData>>>();
   // TODO: this leaks memory
   private final Map<Method, MethodData> methodsByMethod = new MapMaker().makeComputingMap(new MethodResolver());
   private final Map<String, FieldData> fields = new ConcurrentHashMap<String, FieldData>();
   private final ClassLoader loader;
   private final String superClassName;
   private final boolean signitureModified;

   private final static MethodData NULL_METHOD_DATA = new MethodData("", "", "", null, 0);

   ClassData(BaseClassData data, Set<MethodData> addMethods, Set<MethodData> removedMethods, Set<FieldData> addedFields, Set<FieldData> removedFields)
   {
      className = data.getClassName();
      internalName = data.getInternalName();
      loader = data.getLoader();
      superClassName = data.getSuperClassName();
      signitureModified = true;

      for (MethodData m : data.getMethods())
      {
         if (!removedMethods.contains(m))
         {
            addMethod(m);
         }
      }
      for (FieldData f : data.getFields())
      {
         if (!removedFields.contains(f))
         {
            addField(f);
         }
      }

      for (FieldData f : removedFields)
      {
         addField(f);
      }
      for (FieldData f : addedFields)
      {
         addField(f);
      }

      for (MethodData m : removedMethods)
      {
         addMethod(m);
      }

      for (MethodData m : addMethods)
      {
         addMethod(m);
      }

   }

   public MethodData getData(Method method)
   {
      MethodData res = methodsByMethod.get(method);
      if (res == NULL_METHOD_DATA)
      {
         return null;
      }
      return res;
   }

   ClassData(BaseClassData data)
   {
      className = data.getClassName();
      internalName = data.getInternalName();
      loader = data.getLoader();
      superClassName = data.getSuperClassName();
      for (MethodData m : data.getMethods())
      {
         addMethod(m);
      }
      for (FieldData f : data.getFields())
      {
         addField(f);
      }
      signitureModified = false;
   }

   public boolean isSignitureModified()
   {
      return signitureModified;
   }

   /**
    * Searches through parent classloaders of the classes class loader to find
    * the ClassData structure for the super class
    * 
    * @return
    */
   public ClassData getSuperClassInformation()
   {
      if (superClassName == null)
      {
         return null;
      }
      ClassData superClassInformation = ClassDataStore.getModifiedClassData(loader, superClassName);
      ClassLoader l = loader;
      while (superClassInformation == null && l != null)
      {
         l = l.getParent();
         superClassInformation = ClassDataStore.getModifiedClassData(l, superClassName);
      }
      return superClassInformation;
   }

   public FieldData getField(String field)
   {
      return fields.get(field);
   }

   public String getSuperClassName()
   {
      return superClassName;
   }

   public ClassLoader getLoader()
   {
      return loader;
   }

   public String getClassName()
   {
      return className;
   }

   public String getInternalName()
   {
      return internalName;
   }

   public void addMethod(MethodData data)
   {

      if (!methods.containsKey(data.getMethodName()))
      {
         methods.put(data.getMethodName(), new HashMap<String, Set<MethodData>>());
      }
      Map<String, Set<MethodData>> mts = methods.get(data.getMethodName());
      if (!mts.containsKey(data.getArgumentDescriptor()))
      {
         mts.put(data.getArgumentDescriptor(), new HashSet<MethodData>());
      }
      Set<MethodData> rr = mts.get(data.getArgumentDescriptor());
      rr.add(data);

   }

   /**
    * replaces a method if it already exists
    * 
    * @param data
    */
   public void replaceMethod(MethodData data)
   {
      if (!methods.containsKey(data.getMethodName()))
      {
         methods.put(data.getMethodName(), new HashMap<String, Set<MethodData>>());
      }
      Map<String, Set<MethodData>> mts = methods.get(data.getMethodName());
      Set<MethodData> rr = new HashSet<MethodData>();
      mts.put(data.getArgumentDescriptor(), rr);
      rr.add(data);

   }

   public void addField(FieldData data)
   {
      fields.put(data.getName(), data);
   }

   public Collection<MethodData> getMethods()
   {

      Set<MethodData> results = new HashSet<MethodData>();
      for (String nm : methods.keySet())
      {

         for (String i : methods.get(nm).keySet())
         {
            results.addAll(methods.get(nm).get(i));
         }
      }
      return results;
   }

   public Collection<FieldData> getFields()
   {
      return fields.values();
   }

   /**
    * gets the method data based on name and signature. If there is multiple
    * methods with the same name and signature it is undefined which one will be
    * returned
    * 
    * @param name
    * @param arguments
    * @return
    */
   public MethodData getMethodData(String name, String arguments)
   {
      Map<String, Set<MethodData>> r = methods.get(name);
      if (r == null)
      {
         return null;
      }
      Set<MethodData> ms = r.get(arguments);

      if (ms == null)
      {
         return null;
      }
      return ms.iterator().next();
   }

   private static class MethodResolver implements Function<Method, MethodData>
   {

      public MethodData apply(Method from)
      {
         ClassData dta = ClassDataStore.getModifiedClassData(from.getDeclaringClass().getClassLoader(), from.getDeclaringClass().getName());
         if (dta == null)
         {
            return NULL_METHOD_DATA;
         }
         String descriptor = DescriptorUtils.getDescriptor(from);
         for (MethodData m : dta.getMethods())
         {
            if (m.getMethodName().equals(from.getName()) && descriptor.equals(m.getDescriptor()))
            {
               return m;
            }
         }
         return NULL_METHOD_DATA;

      }

   }
}

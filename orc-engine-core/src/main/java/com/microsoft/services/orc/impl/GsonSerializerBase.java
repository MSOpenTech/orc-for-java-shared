package com.microsoft.services.orc.impl;


import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.services.orc.Constants;
import com.microsoft.services.orc.ODataBaseEntity;
import com.microsoft.services.orc.interfaces.JsonSerializer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.microsoft.services.orc.Helpers.getReservedNames;

/**
 * The type Gson serializer.
 */
public abstract class GsonSerializerBase implements JsonSerializer {
    private static Map<String, Class<?>> cachedClassesFromOData = new ConcurrentHashMap<String, Class<?>>();

    private Gson createGson() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .registerTypeAdapter(Calendar.class, new CalendarTypeAdapter())
                .registerTypeAdapter(GregorianCalendar.class, new CalendarTypeAdapter())
                .registerTypeAdapter(byte[].class, getByteArrayTypeAdapter())
                .create();
    }

    protected abstract ByteArrayTypeAdapterBase getByteArrayTypeAdapter();

    @Override
    public String serialize(Object objectToSerialize) {
        Gson serializer = createGson();
        JsonElement json = serializer.toJsonTree(objectToSerialize);
        sanitizePostSerialization(json);

        return json.toString();
    }

    @Override
    public <E> E deserialize(String payload, Class<E> clazz) {
        Gson serializer = createGson();
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(payload);
        sanitizeForDeserialization(json);

        Package pkg = clazz.getPackage();
        Class overridenClass = getClassFromJson(json, pkg);

        if (overridenClass != null) {
            clazz = overridenClass;
        }

        E odataEntity = serializer.fromJson(json, clazz);

        referenceParents(odataEntity, null, null);

        return odataEntity;
    }

    private void referenceParents(Object objToAnalyze, ODataBaseEntity parent, String referenceProperty)  {
        if (objToAnalyze == null) {
            return;
        }

        Class objClass = objToAnalyze.getClass();

        if (objToAnalyze instanceof ParentReferencedList) {
            ParentReferencedList list = (ParentReferencedList)objToAnalyze;

            for (Object subObject : list) {
                referenceParents(subObject, parent, referenceProperty);
            }
        }
        if (objToAnalyze instanceof List) {
            List list = (List)objToAnalyze;

            for (Object subObject : list) {
                referenceParents(subObject, parent, referenceProperty);
            }
        } else if (objToAnalyze instanceof ODataBaseEntity) {
            ODataBaseEntity entity = (ODataBaseEntity)objToAnalyze;
            if (parent != null) {
                entity.setParent(parent, referenceProperty);
            }

            for (Field field : getAllFields(objClass, ODataBaseEntity.class)) {
                field.setAccessible(true);

                try {
                    Object fieldValue = field.get(objToAnalyze);
                    if (fieldValue instanceof List && !(fieldValue instanceof ParentReferencedList)) {
                        List originalList = (List)fieldValue;
                        ParentReferencedList wrapperList = new ParentReferencedList(originalList, entity, field.getName());
                        field.set(entity, wrapperList);
                        referenceParents(wrapperList, wrapperList, null);
                    } else {
                        referenceParents(fieldValue, entity, field.getName());
                    }

                } catch (IllegalAccessException e) {
                }
            }
        }
    }

    private Iterable<? extends Field> getAllFields(Class clazz, Class topClass) {
        List<Field> fields = new ArrayList<Field>();

        while (clazz != topClass) {
            for (Field f : clazz.getDeclaredFields()) {
                fields.add(f);
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    private class ParentReferencedList<E> extends ODataBaseEntity implements List<E> { // necesito que este y que odatabaseentity implementen notifypropertychanged, para que cuando encuentra la lista pase siempre esa lista como objeto para notificar en la recursion, en vez de el odatabaseentity

        List<E> wrappedList;
        ODataBaseEntity parent;
        String referenceProperty;

        public ParentReferencedList(List<E> wrappedlist, ODataBaseEntity parent, String referenceProperty) {
            this.wrappedList = wrappedlist;
            this.parent = parent;
            this.referenceProperty = referenceProperty;
        }

        public void valueChanged(String property, Object payload) {
            valueChanged();
        }

        void valueChanged() {
            parent.valueChanged(referenceProperty, this);
        }

        @Override
        public int size() {
            return wrappedList.size();
        }

        @Override
        public boolean isEmpty() {
            return wrappedList.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return wrappedList.contains(o);
        }

        @Override
        public Iterator<E> iterator() {
            return wrappedList.iterator();
        }

        @Override
        public Object[] toArray() {
            return wrappedList.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return wrappedList.toArray(a);
        }

        @Override
        public boolean add(E e) {
            boolean ret = wrappedList.add(e);
            valueChanged();
            return ret;
        }

        @Override
        public boolean remove(Object o) {
            boolean ret =  wrappedList.remove(o);
            valueChanged();
            return ret;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return wrappedList.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            boolean ret =  wrappedList.addAll(c);
            valueChanged();
            return ret;
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            boolean ret =  wrappedList.addAll(c);
            valueChanged();
            return ret;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean ret =  wrappedList.removeAll(c);
            valueChanged();
            return ret;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean ret = wrappedList.retainAll(c);
            valueChanged();
            return ret;
        }

        @Override
        public void clear() {
            wrappedList.clear();
            valueChanged();
        }

        @Override
        public E get(int index) {
            return wrappedList.get(index);
        }

        @Override
        public E set(int index, E element) {
            E ret = wrappedList.set(index, element);
            valueChanged();
            return ret;
        }

        @Override
        public void add(int index, E element) {
            wrappedList.add(index, element);
            valueChanged();
        }

        @Override
        public E remove(int index) {
            E ret =  wrappedList.remove(index);
            valueChanged();
            return ret;
        }

        @Override
        public int indexOf(Object o) {
            return wrappedList.indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return wrappedList.lastIndexOf(o);
        }

        @Override
        public ListIterator<E> listIterator() {
            return wrappedList.listIterator();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            return wrappedList.listIterator(index);
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            return wrappedList.subList(fromIndex, toIndex);
        }
    }

    protected Class getClassFromJson(JsonElement json, Package pkg) {
        try {
            if (json.isJsonObject()) {
                JsonObject jsonObject = json.getAsJsonObject();

                if (jsonObject.has(Constants.ODATA_TYPE_PROPERTY_NAME)) {
                    String dataType = jsonObject.get(Constants.ODATA_TYPE_PROPERTY_NAME).getAsString();
                    if (cachedClassesFromOData.containsKey(dataType)) {
                        return cachedClassesFromOData.get(dataType);
                    }

                    String[] parts = dataType.split("\\.");
                    String className = parts[parts.length - 1];

                    String classFullName = pkg.getName() + "." + className;
                    Class<?> derivedClass = Class.forName(classFullName);

                    ODataBaseEntity instance = (ODataBaseEntity)derivedClass.newInstance();

                    Field field = ODataBaseEntity.class.getDeclaredField(Constants.ODATA_TYPE_PROPERTY_NAME);
                    if (field != null) {
                        field.setAccessible(true);
                        String val = (String) field.get(instance);
                        if (val.equals(dataType)) {
                            cachedClassesFromOData.put(dataType, derivedClass);
                            return derivedClass;
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
            // if, for any reason, the sub-class cannot be loaded, just continue and the base class will
            // be used for serialization
        }

        return null;
    }

    @Override
    public <E> List<E> deserializeList(String payload, Class<E> clazz) {
        Gson serializer = createGson();

        JsonParser parser = new JsonParser();
        JsonObject json = (JsonObject) parser.parse(payload);

        JsonElement jsonArray = json.get("value");
        sanitizeForDeserialization(jsonArray);

        Package pkg = clazz.getPackage();
        ArrayList<E> arrayList = new ArrayList<E>();

        for(JsonElement item : jsonArray.getAsJsonArray()) {
            Class currentClass = clazz;
            Class overridenClass = getClassFromJson(item, pkg);

            if (overridenClass != null) {
                currentClass = overridenClass;
            }

            E deserializedItem  = (E) serializer.fromJson(item, currentClass);
            arrayList.add(deserializedItem);
        }

        return arrayList;
    }

    private void sanitizePostSerialization(JsonElement json) {
        if (json.isJsonArray()) {
            JsonArray jsonArray = json.getAsJsonArray();
            for (JsonElement subElement : jsonArray) {
                sanitizePostSerialization(subElement);
            }
        } else if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();

            Set<Map.Entry<String, JsonElement>> entries = new HashSet<Map.Entry<String, JsonElement>>(jsonObject.entrySet());

            for (Map.Entry<String, JsonElement> entry : entries) {
                String propertyName = entry.getKey();
                JsonElement subElement = entry.getValue();

                if (propertyName.startsWith(Constants.PROPERTY_NAME_IGNORE_PREFIX)) {
                    jsonObject.remove(propertyName);
                    continue;
                }

                String newName = propertyName;

                if (newName.startsWith(Constants.PROPERTY_NAME_RESERVED_PREFIX)) {
                    newName = newName.substring(Constants.PROPERTY_NAME_RESERVED_PREFIX.length());
                    if (getReservedNames().contains(newName)) {
                        jsonObject.remove(newName);
                        jsonObject.add(propertyName, subElement);
                    }
                } else if (propertyName.equals(Constants.ODATA_TYPE_PROPERTY_NAME)) {
                    jsonObject.remove(Constants.ODATA_TYPE_PROPERTY_NAME);
                    jsonObject.add(Constants.ODATA_TYPE_JSON_PROPERTY, subElement);
                }

                sanitizePostSerialization(subElement);
            }
        }

    }

    private void sanitizeForDeserialization(JsonElement json) {
        if (json.isJsonArray()) {
            JsonArray jsonArray = json.getAsJsonArray();
            for (JsonElement subElement : jsonArray) {
                sanitizeForDeserialization(subElement);
            }
        } else if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();

            Set<Map.Entry<String, JsonElement>> entries = new HashSet<Map.Entry<String, JsonElement>>(jsonObject.entrySet());

            for (Map.Entry<String, JsonElement> entry : entries) {
                String propertyName = entry.getKey();
                JsonElement subElement = entry.getValue();

                String newName = Constants.PROPERTY_NAME_RESERVED_PREFIX + propertyName;
                if (getReservedNames().contains(propertyName)) {
                    jsonObject.remove(propertyName);
                    jsonObject.add(newName, subElement);
                } else {
                    String oDataTypeName = Constants.ODATA_TYPE_PROPERTY_NAME;
                    if (propertyName.equals(Constants.ODATA_TYPE_JSON_PROPERTY)) {
                        jsonObject.remove(propertyName);
                        jsonObject.add(oDataTypeName, subElement);
                    }
                }

                sanitizePostSerialization(subElement);
            }
        }
    }

    @Override
    public String jsonObjectFromJsonMap(Map<String, String> map) {
        JsonObject object = new JsonObject();
        JsonParser parser = new JsonParser();

        for (String key : map.keySet()) {
            String jsonString = map.get(key);
            JsonElement element = parser.parse(jsonString);
            object.add(key, element);
        }

        return object.toString();
    }
}

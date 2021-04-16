package util;

import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static util.ValueUtil.invokeMethod;

public class ValueJsonSerializer {
    public static final String JAVA_LANG_OBJECT = "java.lang.Object";

    private static long timeLimit = 7000;
    private static long timeStamp;

    public static String toJson(Value value, ThreadReference thread, Set<Long> refPath) {
        timeStamp = System.currentTimeMillis();
        return toJsonInner(value, thread, refPath);
    }

    private static String toJsonInner(Value value, ThreadReference thread, Set<Long> refPath) {
        if (System.currentTimeMillis() - timeStamp > timeLimit) {
            throw new JsonSerializeException("JSON serializing timed out, probably the object is too big to JSON.");
        }

        if (value == null) {
            return null;
        }

        if (value instanceof ObjectReference) {
            long id = ((ObjectReference) value).uniqueID();
            if (refPath.contains(id)) {
                return null;
            }
            refPath = new HashSet<>(refPath);
            refPath.add(id);
        }

        {
            if (value instanceof IntegerValue) {
                return String.valueOf(((IntegerValue) value).value());
            }
            if (value instanceof DoubleValue) {
                return String.valueOf(((DoubleValue) value).value());
            }
            if (value instanceof FloatValue) {
                return String.valueOf(((FloatValue) value).value());
            }
            if (value instanceof ShortValue) {
                return String.valueOf(((ShortValue) value).value());
            }
            if (value instanceof ByteValue) {
                return String.valueOf(((ByteValue) value).value());
            }
            if (value instanceof LongValue) {
                return String.valueOf(((LongValue) value).value());
            }
            if (value instanceof CharValue) {
                return "\"" + escape(String.valueOf(((CharValue) value).value())) + "\"";
            }
            if (value instanceof BooleanValue) {
                return String.valueOf(((BooleanValue) value).value());
            }
            if (value instanceof StringReference) {
                return "\"" + escape(((StringReference) value).value()) + "\"";
            }
        }

        if (value instanceof ArrayReference) {
            ArrayReference arrayValue = (ArrayReference) value;
            StringBuilder str = new StringBuilder();
            str.append("[");
            for (Value v : arrayValue.getValues()) {
                str.append(toJsonInner(v, thread, refPath));
                str.append(",");
            }
            if (arrayValue.length() > 0) {
                str.delete(str.length() - 1, str.length());
            }
            str.append("]");
            return str.toString();
        }

        if (value instanceof ObjectReference) {
            Set<String> allInheritedTypes = getAllInheritedTypes(value);
            ObjectReference objectValue = (ObjectReference) value;

            if (isSimpleObject(allInheritedTypes)) {
                return toJsonInner(objectValue.getValue(((ClassType)value.type()).fieldByName("value")), thread, refPath);
            } else if (allInheritedTypes.contains("java.util.Map")) {
                ObjectReference keySet = (ObjectReference) invokeMethod(objectValue, "keySet", thread);
                if (keySet == null) {
                    throw new JsonSerializeException("KeySet of Map returns null : " + toValRefString(objectValue));
                }
                ArrayReference keyArr = (ArrayReference) invokeMethod(keySet, "toArray", thread);
                if (keyArr == null) {
                    throw new JsonSerializeException("KeySet convert failed : " + toValRefString(keySet));
                }
                StringBuilder str = new StringBuilder();
                str.append("{");
                for (Value key : keyArr.getValues()) {
                    Value val = invokeMethod(objectValue, "get", thread, key);
                    String keyStr;
                    if (isSimpleValue(key)) {
                        String simpleValStr = toJsonInner(key, thread, refPath);
                        if (simpleValStr != null && simpleValStr.startsWith("\"")) {
                            keyStr = simpleValStr;
                        } else {
                            keyStr = "\"" + simpleValStr + "\"";
                        }
                    } else {
                        keyStr = "\"" + toValRefString((ObjectReference) key) + "\"";
                    }

                    str.append(keyStr).append(":").append(toJsonInner(val, thread, refPath));
                    str.append(",");
                }
                if (keyArr.length() > 0) {
                    str.delete(str.length() - 1, str.length());
                }
                str.append("}");
                return str.toString();
            } else if (allInheritedTypes.contains("java.util.Collection")) {
                return toJsonInner(invokeMethod(objectValue, "toArray", thread), thread, refPath);
            }

            for (String type : allInheritedTypes) {
                if (type.startsWith("java")) {
                    if (!objectValue.referenceType().methodsByName("toString").get(0)
                            .declaringType().name().equals(JAVA_LANG_OBJECT)) {
                        return toJsonInner(invokeMethod(objectValue, "toString", thread), thread, refPath);
                    } else {
                        return "\"" + toValRefString(objectValue) + "\"";
                    }
                }
            }


            StringBuilder str = new StringBuilder();
            str.append("{");
            boolean hasOne = false;
            for (Map.Entry<Field, Value> fieldValueEntry : objectValue.getValues(((ClassType)value.type()).allFields()).entrySet()) {
                String fieldName = fieldValueEntry.getKey().name();
                String fieldValue = toJsonInner(fieldValueEntry.getValue(), thread, refPath);
                str.append("\"").append(fieldName).append("\"");
                str.append(":");
                str.append(fieldValue);
                str.append(",");
                hasOne = true;
            }
            if (hasOne) {
                str.delete(str.length() - 1, str.length());
            }
            str.append("}");

            return str.toString();
        }

        throw new JsonSerializeException("Unforeseen value type for : " + value.type().name());
    }

    private static boolean isSimpleObject(Set<String> allInheritedTypes) {
        return allInheritedTypes.contains("java.lang.Integer")
                || allInheritedTypes.contains("java.lang.Byte")
                || allInheritedTypes.contains("java.lang.Double")
                || allInheritedTypes.contains("java.lang.Float")
                || allInheritedTypes.contains("java.lang.Long")
                || allInheritedTypes.contains("java.lang.Short")
                || allInheritedTypes.contains("java.lang.Boolean")
                || allInheritedTypes.contains("java.lang.Character");
    }

    private static boolean isSimpleValue(Value value) {
        if (value == null || value instanceof PrimitiveValue || value instanceof StringReference) {
            return true;
        } else if (value instanceof ObjectReference) {
            return isSimpleObject(getAllInheritedTypes(value));
        } else {
            return false;
        }
    }

    @NotNull
    private static String toValRefString(ObjectReference valRef) {
        long id = valRef.uniqueID();
        return valRef.type().name() + "(id=" + id + ")";
    }

    @NotNull
    private static Set<String> getAllInheritedTypes(Value value) {
        Set<String> allInheritedTypes = new HashSet<>();
        ClassType type = ((ClassType) value.type());
        allInheritedTypes.add(type.name());
        for (InterfaceType iType : type.allInterfaces()) {
            allInheritedTypes.add(iType.name());
        }
        while (type.superclass() != null) {
            type = type.superclass();
            allInheritedTypes.add(type.name());
        }
        allInheritedTypes.remove(JAVA_LANG_OBJECT);
        return allInheritedTypes;
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
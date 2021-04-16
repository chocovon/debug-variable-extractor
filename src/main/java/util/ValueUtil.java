package util;

import com.sun.jdi.*;

import java.util.Arrays;
import java.util.List;

public class ValueUtil {
    public static Value invokeMethod(ObjectReference object, String methodName, ThreadReference thread, Value... args) {
        List<Method> methods = object.referenceType().methodsByName(methodName);
        if (methods.size() == 0) {
            throw new NoSuchMethodError(methodName);
        }
        try {
            for (Method m : methods) {
                List<Type> argType = m.argumentTypes();
                if (argType.size() == args.length - 1 || argType.size() == args.length) {
                    try {
                        return object.invokeMethod(thread, m, Arrays.asList(args), 0);
                    } catch (IllegalArgumentException ignored) {
                        //after all method tried then throw this exception
                        //todo using local method
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}

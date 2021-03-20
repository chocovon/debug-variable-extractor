package action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import util.JsonSerializeException;
import util.ValueJsonSerializer;
import util.ValueTransferException;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;

public class DebugVarAction extends XDebuggerTreeActionBase {
    @Override
    protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
        XValue xValue = node.getValueContainer();
        try {
            Object valueDescriptor = invokeMethod(xValue, "getDescriptor");
            Object evalContext = getField(valueDescriptor, "myStoredEvaluationContext");
            Object frame = invokeMethod(evalContext, "getFrameProxy");
            Object threadProxy = invokeMethod(frame, "threadProxy");
            ThreadReference thread = (ThreadReference) invokeMethod(threadProxy, "getObjectReference");
            Value val = (Value) invokeMethod(valueDescriptor, "getValue");
            String str = ValueJsonSerializer.toJson(val, thread, new HashSet<>());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(str), null);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | NoSuchFieldException ex) {
            ex.printStackTrace();
            throw new ValueTransferException("Plugin cannot work properly in current version of IDEA.");
        }
    }

    private Object getField(Object object, String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Class<?> clz = object.getClass();
        while (clz != Object.class) {
            Field[] fields = clz.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equals(fieldName)) {
                    field.setAccessible(true);
                    return field.get(object);
                }
            }
            clz = clz.getSuperclass();
        }
        throw new NoSuchFieldException();
    }

    //simply invoke first matched method
    private Object invokeMethod(Object object, String methodName, Object... args) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Class<?> clz = object.getClass();
        if (args.length > 0) {
            for (Method m : clz.getMethods()) {
                if (m.getName().equals(methodName)) {
                    return m.invoke(object, args);
                }
            }
            throw new NoSuchMethodException(methodName);
        } else {
            Method m = clz.getMethod(methodName);
            return m.invoke(object);
        }
    }
}
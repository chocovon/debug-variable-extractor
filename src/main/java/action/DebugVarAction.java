package action;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import util.ValueJsonSerializer;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.HashSet;
import java.util.Objects;

public class DebugVarAction extends XDebuggerTreeActionBase {
    @Override
    protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
        XValue xValue = node.getValueContainer();
        ValueDescriptorImpl valueDescriptor = ((JavaValue) xValue).getDescriptor();
        EvaluationContext evalContext = valueDescriptor.getStoredEvaluationContext();
        ThreadReferenceProxyImpl threadProxy = (ThreadReferenceProxyImpl) Objects.requireNonNull(evalContext.getFrameProxy()).threadProxy();
        ThreadReference thread = (ThreadReference) threadProxy.getObjectReference();
        Value val = valueDescriptor.getValue();
        String str = ValueJsonSerializer.toJson(val, thread, new HashSet<>());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(str), null);
    }
}
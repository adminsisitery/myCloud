package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.lang.reflect.Method;
import java.util.*;

/** Do not use DefaultToolExecutor: LC4j 1.3 returns exception messages as model-visible data. */
final class AgentToolInvoker {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final Object target;
    private final Method method;
    AgentToolInvoker(Object target,Method method) { this.target=target;this.method=method; }
    String execute(ToolExecutionRequest request) throws Exception {
        var node=JSON.readTree(request.arguments()==null?"{}":request.arguments());
        if (!node.isObject()) throw new IllegalArgumentException("Arguments must be an object");
        var parameters=method.getParameters();
        Set<String> allowed=new HashSet<>();
        Object[] values=new Object[parameters.length];
        for (int i=0;i<parameters.length;i++) {
            String name=parameters[i].getName(); allowed.add(name);
            if (!node.has(name)) throw new IllegalArgumentException("Missing argument");
            values[i]=JSON.convertValue(node.get(name),JSON.getTypeFactory().constructType(parameters[i].getParameterizedType()));
        }
        var names=node.fieldNames();
        while(names.hasNext()) if (!allowed.contains(names.next())) throw new IllegalArgumentException("Unexpected argument");
        Object value;
        try { value=method.invoke(target,values); }
        catch (java.lang.reflect.InvocationTargetException e) {
            if(e.getCause() instanceof org.springframework.web.server.ResponseStatusException status) {
                String reason=status.getReason();
                return "ERROR: " + (reason==null ? "操作被拒绝，请重新检查权限或文件状态" : reason.substring(0,Math.min(200,reason.length())));
            }
            throw e;
        }
        return value instanceof String s?s:JSON.writeValueAsString(value);
    }
}

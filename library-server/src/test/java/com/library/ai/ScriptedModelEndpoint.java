package com.library.ai;

import com.library.ai.contract.ModelEndpoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 模型系统边界的确定性脚本 Adapter；不替代任何项目内模块。 */
public final class ScriptedModelEndpoint implements ModelEndpoint {
    private final ArrayDeque<Object> script=new ArrayDeque<>();
    private final List<ModelRequest> requests=new ArrayList<>();
    public synchronized void reset(){script.clear();requests.clear();}
    public synchronized void then(ModelResponse response){script.add(response);}
    public synchronized void fail(String reason){script.add(new ModelFailure(reason));}
    public synchronized List<ModelRequest> requests(){return List.copyOf(requests);}
    @Override public synchronized ModelResponse complete(ModelRequest request) throws ModelFailure {
        requests.add(request);
        if(script.isEmpty())return ModelResponse.answer("ok");
        var next=script.remove();
        if(next instanceof ModelFailure failure)throw failure;
        return (ModelResponse)next;
    }
}

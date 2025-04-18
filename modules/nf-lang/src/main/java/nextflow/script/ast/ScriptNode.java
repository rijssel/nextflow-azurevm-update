/*
 * Copyright 2024-2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.script.ast;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.SourceUnit;

/**
 * The top-level AST node for a script.
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
public class ScriptNode extends ModuleNode {
    private String shebang;
    private List<FeatureFlagNode> featureFlags = new ArrayList<>();
    private List<IncludeNode> includes = new ArrayList<>();
    private List<ParamNode> params = new ArrayList<>();
    private WorkflowNode entry;
    private OutputBlockNode outputs;
    private List<WorkflowNode> workflows = new ArrayList<>();
    private List<ProcessNode> processes = new ArrayList<>();
    private List<FunctionNode> functions = new ArrayList<>();

    public ScriptNode(SourceUnit sourceUnit) {
        super(sourceUnit);
    }

    public String getShebang() {
        return shebang;
    }

    /**
     * Get the list of script declarations in canonical order.
     */
    public List<ASTNode> getDeclarations() {
        var declarations = new ArrayList<ASTNode>();
        declarations.addAll(featureFlags);
        declarations.addAll(includes);
        declarations.addAll(params);
        if( entry != null )
            declarations.add(entry);
        if( outputs != null )
            declarations.add(outputs);
        for( var wn : workflows ) {
            if( !wn.isEntry() )
                declarations.add(wn);
        }
        declarations.addAll(processes);
        declarations.addAll(functions);
        for( var cn : getClasses() ) {
            if( cn.isEnum() )
                declarations.add(cn);
        }
        return declarations;
    }

    public List<FeatureFlagNode> getFeatureFlags() {
        return featureFlags;
    }

    public List<IncludeNode> getIncludes() {
        return includes;
    }

    public List<ParamNode> getParams() {
        return params;
    }

    public WorkflowNode getEntry() {
        return entry;
    }

    public OutputBlockNode getOutputs() {
        return outputs;
    }

    public List<WorkflowNode> getWorkflows() {
        return workflows;
    }

    public List<ProcessNode> getProcesses() {
        return processes;
    }

    public List<FunctionNode> getFunctions() {
        return functions;
    }

    public void setShebang(String shebang) {
        this.shebang = shebang;
    }

    public void addFeatureFlag(FeatureFlagNode featureFlag) {
        featureFlags.add(featureFlag);
    }

    public void addInclude(IncludeNode includeNode) {
        includes.add(includeNode);
    }

    public void addParam(ParamNode paramNode) {
        params.add(paramNode);
    }

    public void setEntry(WorkflowNode entry) {
        this.entry = entry;
    }

    public void setOutputs(OutputBlockNode outputs) {
        this.outputs = outputs;
    }

    public void addWorkflow(WorkflowNode workflowNode) {
        workflows.add(workflowNode);
    }

    public void addProcess(ProcessNode processNode) {
        processes.add(processNode);
    }

    public void addFunction(FunctionNode functionNode) {
        functions.add(functionNode);
    }
}

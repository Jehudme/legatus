package dev.legatus.server.features.workflows;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.*;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorkflowStep {

    @JsonProperty("step_id")
    private String stepId;

    @JsonProperty("function_name")
    private String functionName;

    @JsonProperty("depends_on")
    private List<String> dependsOn = new ArrayList<>();

    @JsonProperty("arguments")
    private Map<String, Object> arguments = new HashMap<>();

    @JsonProperty("env")
    private Map<String, String> env = new HashMap<>();

    @JsonProperty("status")
    private StepStatus status = StepStatus.WAITING;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("output")
    private Object output;

    @JsonProperty("error")
    private String error;

    public WorkflowStep() {}

    public WorkflowStep(String stepId, String functionName, List<String> dependsOn, Map<String, Object> arguments, Map<String, String> env) {
        this.stepId = stepId;
        this.functionName = functionName;
        if (dependsOn != null) this.dependsOn = new ArrayList<>(dependsOn);
        if (arguments != null) this.arguments = new HashMap<>(arguments);
        if (env != null) this.env = new HashMap<>(env);
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public StepStatus getStatus() {
        return status;
    }

    public void setStatus(StepStatus status) {
        this.status = status;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

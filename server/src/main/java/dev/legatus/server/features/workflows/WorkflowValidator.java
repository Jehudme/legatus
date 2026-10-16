package dev.legatus.server.features.workflows;

import java.util.*;

public class WorkflowValidator {

    public static class WorkflowCycleException extends RuntimeException {
        public WorkflowCycleException(String message) {
            super(message);
        }
    }

    /**
     * Validates that the workflow graph is a valid Directed Acyclic Graph (DAG)
     * using Kahn's algorithm for topological sorting.
     *
     * @param steps map of stepId -> WorkflowStep
     * @return topologically sorted list of step IDs
     * @throws IllegalArgumentException if a step references an undefined dependency
     * @throws WorkflowCycleException if a cyclic dependency is detected
     */
    public static List<String> validateAndSort(Map<String, WorkflowStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        for (String id : steps.keySet()) {
            inDegree.put(id, 0);
            adjacency.put(id, new ArrayList<>());
        }

        for (WorkflowStep step : steps.values()) {
            String stepId = step.getStepId();
            if (step.getDependsOn() != null) {
                for (String dep : step.getDependsOn()) {
                    if (!steps.containsKey(dep)) {
                        throw new IllegalArgumentException(
                            String.format("Step '%s' depends on unknown step '%s'", stepId, dep)
                        );
                    }
                    adjacency.get(dep).add(stepId);
                    inDegree.put(stepId, inDegree.get(stepId) + 1);
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sortedOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedOrder.add(current);

            for (String neighbor : adjacency.get(current)) {
                int updated = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, updated);
                if (updated == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sortedOrder.size() != steps.size()) {
            List<String> cyclicNodes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cyclicNodes.add(entry.getKey());
                }
            }
            throw new WorkflowCycleException(
                String.format("Cyclic dependency detected in workflow DAG involving steps: %s", cyclicNodes)
            );
        }

        return sortedOrder;
    }
}

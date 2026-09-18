package com.ff.mycloud.ai.agent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanParser {
    private final ObjectMapper json;
    public AgentPlanParser(ObjectMapper json) { this.json = json; }

    public TaskPlan parse(String response, String goal, int maxSteps) {
        try {
            if (response == null || response.length() > 20000) throw new IllegalArgumentException();
            int start = response.indexOf('{'), end = response.lastIndexOf('}');
            var node = json.readTree(response.substring(start, end + 1));
            var steps = node.path("steps");
            if (!steps.isArray() || steps.isEmpty() || steps.size() > maxSteps) throw new IllegalArgumentException();
            var result = new ArrayList<PlanStep>();
            for (var step : steps) {
                String description = step.isTextual() ? step.asText() : step.path("description").asText();
                if (description.isBlank() || description.length() > 1500) throw new IllegalArgumentException();
                result.add(PlanStep.pending(result.size(), description));
            }
            return TaskPlan.builder().goal(goal).steps(result).build();
        } catch (Exception ignored) {
            return TaskPlan.builder().goal(goal).steps(new ArrayList<>(java.util.List.of(PlanStep.pending(0, goal)))).build();
        }
    }
}

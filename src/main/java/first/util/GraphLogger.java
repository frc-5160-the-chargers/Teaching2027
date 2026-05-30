package first.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.system.Filesystem;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public class GraphLogger {
    public static final int MAX_HISTORY_LENGTH = 5;

    private static final GraphLogger instance = new GraphLogger();

    public static GraphLogger getDefault() {
        return instance;
    }

    protected GraphLogger() {}

    private record StateMachineGraph(String graph, String[] stateDefinitionOrder) {}
    private static class StateMachineFrontmatter {
        @JsonProperty("state_definition_order")
        public String[] stateDefinitionOrder;
    }

    private boolean hasStarted = false;
    private BiConsumer<String, String> logger = (key, value) -> {};
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final Pattern frontmatterPattern = Pattern.compile("^---\\s*(.*?)\\s*---", Pattern.DOTALL);
    private final Map<String, StateMachineGraph> stateMachineGraphs = new HashMap<>();

    public void start(BiConsumer<String, String> logger) {
        this.hasStarted = true;
        this.logger = logger;
        loadStateMachineGraphs();
    }

    private void loadStateMachineGraphs() {
        var stateMachineGraphsDir = new File(Filesystem.getDeployDirectory(), "stateMachineGraphs").listFiles();
        if (stateMachineGraphsDir == null) return;
        for (var file: stateMachineGraphsDir) {
            if (!file.getName().endsWith(".mermaid")) continue;
            var stateMachineName = file.getName().replace(".mermaid", "");
            try {
                var graph = Files.readString(file.toPath());
                if (!graph.contains("---")) continue;
                var frontmatter = extractFrontmatter(graph);
                if (frontmatter == null) continue;
                var stateDefOrder = mapper.readValue(frontmatter, StateMachineFrontmatter.class).stateDefinitionOrder;
                graph = graph.substring(graph.lastIndexOf("---") + 3);
                stateMachineGraphs.put(stateMachineName, new StateMachineGraph(graph, stateDefOrder));
                logger.accept("StateMachineGraphs/" + stateMachineName, graph);
            } catch (Exception e) {
                DriverStationBackend.reportError("The graph of " + stateMachineName + " could not be loaded. Error: " + e.getMessage(), false);
            }
        }
    }

    private String extractFrontmatter(String mermaidCode) {
        var matcher = frontmatterPattern.matcher(mermaidCode);
        return matcher.find() ? matcher.group(1) : null;
    }

    void updateStateMachineGraph(
        String stateMachineName,
        List<StateMachine.State> history,
        List<StateMachine.State> allStates
    ) {
        if (!hasStarted) return;
        var graph = stateMachineGraphs.get(stateMachineName);
        if (graph == null) {
            DriverStationBackend.reportError("The graph of " + stateMachineName + " could not be loaded.", false);
            return;
        }
        var stateDefs = graph.stateDefinitionOrder;
        var historyAsStringList = history.stream().map(s -> stateDefs[allStates.indexOf(s)]).toList();
        var graphStr = "---\nhistory: " + historyAsStringList + "\n---\n" + graph.graph();
        logger.accept("StateMachineGraphs/" + stateMachineName, graphStr);
    }
}

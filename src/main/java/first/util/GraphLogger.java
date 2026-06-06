// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.system.Filesystem;
import org.yaml.snakeyaml.Yaml;

/**
 * GraphLogger is responsible for managing and logging state machine graphs that have been generated
 * with WPILib's <code>@MakeStateMachineGraph</code> annotation.
 */
public class GraphLogger {
  /** The maximum number of states to keep in the history. */
  public static final int MAX_HISTORY_LENGTH = 4;

  private static final GraphLogger instance = new GraphLogger();

  private record StateMachineGraph(String graph, List<String> stateDefinitionOrder) {}

  /**
   * Returns the default instance of the GraphLogger.
   *
   * @return the default instance
   */
  public static GraphLogger getDefault() {
    return instance;
  }

  /** Default constructor. */
  protected GraphLogger() {}

  private boolean hasStarted = false;
  private BiConsumer<String, String> logger = (key, value) -> {};
  private final Yaml yaml = new Yaml();
  private final Pattern frontmatterPattern =
      Pattern.compile("^---\\s*(.*?)\\s*---", Pattern.DOTALL);
  private final Map<String, StateMachineGraph> stateMachineGraphs = new HashMap<>();

  /**
   * Starts the GraphLogger with the provided logging function.
   *
   * <p>This will load the state machine graphs from the filesystem and start accepting updates.
   *
   * @param logger a consumer that handles the logging of graph data (key, value)
   */
  public void start(BiConsumer<String, String> logger) {
    this.hasStarted = true;
    this.logger = logger;
    loadStateMachineGraphs();
  }

  private void loadStateMachineGraphs() {
    var stateMachineGraphsDir =
        new File(Filesystem.getDeployDirectory(), "stateMachineGraphs").listFiles();
    if (stateMachineGraphsDir == null) {
      return;
    }
    for (var file : stateMachineGraphsDir) {
      if (!file.getName().endsWith(".mermaid")) {
        continue;
      }
      var stateMachineName = file.getName().replace(".mermaid", "");
      try {
        var graph = Files.readString(file.toPath());
        if (!graph.contains("---")) {
          continue;
        }
        var frontmatter = extractFrontmatter(graph);
        var stateDefOrderProperty = frontmatter.get("state_definition_order");
        var stateDefOrder = new ArrayList<String>();
        if (stateDefOrderProperty instanceof List<?> list) {
          for (var item : list) {
            stateDefOrder.add(item.toString());
          }
          graph = graph.substring(graph.lastIndexOf("---") + 3);
          stateMachineGraphs.put(stateMachineName, new StateMachineGraph(graph, stateDefOrder));
          logger.accept("StateMachineGraphs/" + stateMachineName, graph);
        }
      } catch (IOException e) {
        DriverStationBackend.reportError(
            "The graph of " + stateMachineName + " could not be loaded. Error: " + e.getMessage(),
            false);
      }
    }
  }

  private Map<String, Object> extractFrontmatter(String mermaidCode) {
    var matcher = frontmatterPattern.matcher(mermaidCode);
    if (!matcher.find()) {
      return Map.of();
    }
    Map<String, Object> res = yaml.load(matcher.group(1));
    return res == null ? Map.of() : res;
  }

  void updateStateMachineGraph(
      String stateMachineName,
      List<StateMachine.State> history,
      List<StateMachine.State> allStates) {
    if (!hasStarted) {
      return;
    }
    var graph = stateMachineGraphs.get(stateMachineName);
    if (graph == null) {
      DriverStationBackend.reportError(
          "The graph of " + stateMachineName + " could not be loaded.", false);
      return;
    }
    var stateDefs = graph.stateDefinitionOrder;
    var historyAsStringList =
        history.stream().map(s -> stateDefs.get(allStates.indexOf(s))).toList();
    var graphStr = "---\nhistory: " + historyAsStringList + "\n---\n" + graph.graph();
    logger.accept("StateMachineGraphs/" + stateMachineName, graphStr);
  }
}

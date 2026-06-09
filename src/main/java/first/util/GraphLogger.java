// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.util;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.system.Filesystem;

/**
 * GraphLogger is responsible for managing and logging state machine graphs that have been generated
 * with WPILib's <code>@MakeStateMachineGraph</code> annotation.
 */
public class GraphLogger {
  /** The maximum number of states to keep in the history. */
  public static final int MAX_HISTORY_LENGTH = 4;

  private static final GraphLogger instance = new GraphLogger();

  @Json
  static class StateMachineGraph {
    @Json.Raw String transitions = "";
    List<String> stateDefinitionOrder = List.of();
    String initialState = "";
  }

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
  private BiConsumer<String, String> graphLogger = (key, value) -> {};
  private BiConsumer<String, String[]> historyLogger = (key, value) -> {};
  private final JsonType<StateMachineGraph> graphType = Jsonb.instance().type(StateMachineGraph.class);
  private final Map<String, StateMachineGraph> stateMachineGraphs = new HashMap<>();

  /**
   * Starts the GraphLogger with the provided logging function.
   *
   * <p>This will load the state machine graphs from the filesystem and start accepting updates.
   *
   * @param graphLogger a consumer that handles the logging of graph data (key, value)
   * @param historyLogger a consumer that handles the logging of history data (key, value)
   */
  public void start(BiConsumer<String, String> graphLogger, BiConsumer<String, String[]> historyLogger) {
    this.hasStarted = true;
    this.graphLogger = graphLogger;
    this.historyLogger = historyLogger;
    loadStateMachineGraphs();
  }

  private void loadStateMachineGraphs() {
    var stateMachineGraphsDir =
        new File(Filesystem.getDeployDirectory(), "stateMachineGraphData").listFiles();
    if (stateMachineGraphsDir == null) {
      return;
    }
    for (var file : stateMachineGraphsDir) {
      if (!file.getName().endsWith(".json")) {
        continue;
      }
      var stateMachineName = file.getName().replace(".json", "");
      try {
        var data = graphType.fromJson(Files.readString(file.toPath()));
        stateMachineGraphs.put(stateMachineName, data);
        graphLogger.accept("StateMachines/" + stateMachineName + "/graph", data.transitions);
        graphLogger.accept("StateMachines/" + stateMachineName + "/.type", "StateMachineGraph");
      } catch (Exception e) {
        DriverStationBackend.reportError(
            "The graph of " + stateMachineName + " could not be loaded. Error: " + e.getMessage(),
            false);
      }
    }
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
    var historyAsArray = new String[history.size()];
    for (int i = 0; i < history.size(); i++) {
      historyAsArray[i] = stateDefs.get(allStates.indexOf(history.get(i)));
    }
    historyLogger.accept("StateMachines/" + stateMachineName + "/history", historyAsArray);
  }
}

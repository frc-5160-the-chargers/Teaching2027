package first.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.system.Filesystem;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record MermaidGraph(String graph, String[] stateDefinitionOrder) {
    // Reuse the mapper instance to benefit from cached serializers and parsers
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static class MermaidDiagramMetadata {
        @JsonProperty("state_definition_order")
        public String[] stateDefinitionOrder;
    }

    private static String extractFrontmatter(String mermaidCode) {
        // Regex to match anything between the first --- and the second ---
        Pattern pattern = Pattern.compile("^---\\s*(.*?)\\s*---", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(mermaidCode);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null; // No metadata section found
    }

    static Optional<MermaidGraph> loadFromDeployDirectory(String stateMachineName) {
        var file = new File(Filesystem.getDeployDirectory(), "diagrams/" + stateMachineName + ".mermaid");
        try {
            var graphStr = Files.readString(file.toPath());
            var yamlString = extractFrontmatter(graphStr);
            if (yamlString == null) return Optional.empty();
            var stateDefOrder = mapper.readValue(yamlString, MermaidDiagramMetadata.class).stateDefinitionOrder;
            // TODO use proper mermaid graph parsing library
            var cleanedGraphStr = graphStr.substring(graphStr.lastIndexOf("---") + 3);
            cleanedGraphStr = cleanedGraphStr.replaceFirst("class .*\n$", "");
            return Optional.of(new MermaidGraph(cleanedGraphStr, stateDefOrder));
        } catch (Exception e) {
            DriverStationBackend.reportError("Error loading mermaid graph: " + e.getMessage(), false);
            return Optional.empty();
        }
    }
}

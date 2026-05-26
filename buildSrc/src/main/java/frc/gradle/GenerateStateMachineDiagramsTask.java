package frc.gradle;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class GenerateStateMachineDiagramsTask extends DefaultTask {
    private record Transition(String fromState, String toState, String transitionCond) {}

    @Input
    @Optional
    public abstract Property<String> getJavaRoot();

    @TaskAction
    public void run() throws IOException {
        extractFromDirectory(getJavaRoot().getOrElse("src/main/java"));
    }

    private static void extractFromFile(File sourceFile, Map<String, List<Transition>> diagramMap) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(sourceFile);
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            boolean returnsStateMachine = method.getTypeAsString().equals("StateMachine");
            var annotationOpt = method.getAnnotationByName("GenerateDiagram");
            if (!returnsStateMachine || annotationOpt.isEmpty()) return;

            String diagramName = annotationOpt.get().toNormalAnnotationExpr()
                    .flatMap(GenerateStateMachineDiagramsTask::getDiagramNameProperty)
                    .orElse(method.getNameAsString());
            List<Transition> transitions = diagramMap.computeIfAbsent(diagramName, k -> new ArrayList<>());

            method.findAll(MethodCallExpr.class).forEach(call -> {
                boolean isWhen = call.getNameAsString().equals("when");
                boolean isWhenComplete = call.getNameAsString().equals("whenComplete");
                boolean isWhenCompleteAnd = call.getNameAsString().equals("whenCompleteAnd");
                if (!isWhen && !isWhenComplete && !isWhenCompleteAnd) return;

                var toStateCallOpt = call.getScope()
                        .filter(s -> s instanceof MethodCallExpr)
                        .map(s -> ((MethodCallExpr) s));
                if (toStateCallOpt.isEmpty()) return;

                var toStateCall = toStateCallOpt.get();
                var fromStates = new ArrayList<String>();
                var toState = toStateCall.getArguments().getFirst().orElseThrow().toString();
                if (toStateCall.getNameAsString().equals("switchTo")) {
                    fromStates.add(toStateCall.getScope().orElseThrow().toString());
                } else if (toStateCall.getNameAsString().equals("to")) {
                    // If it's just a regular "to", there must be a switchFromAny before it.
                    var argList = toStateCall.getScope()
                            .filter(s -> s instanceof MethodCallExpr)
                            .map(s -> ((MethodCallExpr) s))
                            .map(MethodCallExpr::getArguments);
                    if (argList.isEmpty()) return;
                    for (var arg: argList.get()) {
                        fromStates.add(arg.toString());
                    }
                }

                var transitionCondExpr = call.getArguments().getFirst();
                var transitionCond = "";
                if (isWhenComplete) {
                    transitionCond = "when complete";
                } else {
                    var prefix = isWhen ? "when " : "when complete and ";
                    var expr = transitionCondExpr.orElseThrow().toString();
                    expr = expr.replace("() -> ", "");
                    expr = expr.replace(".getAsBoolean()", "");
                    transitionCond = prefix + expr;
                }

                for (var fromState : fromStates) {
                    transitions.add(new Transition(fromState, toState, transitionCond));
                }
            });
        });
    }

    private static java.util.Optional<String> getDiagramNameProperty(NormalAnnotationExpr expr) {
        return expr.getPairs().stream()
            .filter(p -> p.getNameAsString().equals("diagramName"))
            .findFirst()
            .map(p -> p.getValue().asStringLiteralExpr().asString())
            .filter(s -> !s.isEmpty());
    }

    private static void extractFromDirectory(String rootDir) throws IOException {
        Map<String, List<Transition>> diagramMap = new LinkedHashMap<>();

        Files.walk(Paths.get(rootDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        extractFromFile(path.toFile(), diagramMap);
                    } catch (IOException e) {
                        System.err.println("Failed to parse: " + path + " — " + e.getMessage());
                    }
                });

        for (var entry : diagramMap.entrySet()) {
            var methodName = entry.getKey();
            var transitions = entry.getValue();
            var file = new File("diagrams/" + methodName + ".mermaid");
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (var writer = new FileWriter(file)) {
                writer.write(generateMermaid(transitions));
            }
        }
    }

    private static String generateMermaid(List<Transition> transitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("stateDiagram-v2\n");
        sb.append("    direction LR\n\n");
        for (Transition t : transitions) {
            sb.append("    ")
                    .append(sanitize(t.fromState()))
                    .append(" --> ")
                    .append(sanitize(t.toState()))
                    .append(" : ")
                    .append(sanitize(t.transitionCond()))
                    .append("\n");
        }
        return sb.toString();
    }

    /** Strips characters that break Mermaid identifiers/labels */
    private static String sanitize(String s) {
        return s.replaceAll("[\"'\\n\\r]", "").trim();
    }
}
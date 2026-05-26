package frc.gradle;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@DisableCachingByDefault
public abstract class GenerateStateMachineDiagramsTask extends DefaultTask {
    private record Transition(String fromState, String toState, String transitionCond) {}
    private record FileExtractResult(Map<String, List<Transition>> diagramMap, String initialState) {}

    @Input
    @Optional
    public abstract Property<String> getJavaRoot();

    @TaskAction
    public void run() throws IOException {
        extractFromDirectory(getJavaRoot().getOrElse("src/main/java"));
    }

    private static void extractFromFile(
        File sourceFile,
        Map<String, List<Transition>> diagrams,
        Map<String, String> initialStates
    ) throws IOException {
        // 1. Initialize configuration
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        var returnAnalyzer = new ReturnConditionAnalyzer();
        var cu = StaticJavaParser.parse(sourceFile);
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            boolean returnsStateMachine = method.getTypeAsString().equals("StateMachine");
            var annotationOpt = method.getAnnotationByName("GenerateDiagram");
            if (!returnsStateMachine || annotationOpt.isEmpty()) return;

            String diagramName = annotationOpt.get().toNormalAnnotationExpr()
                    .flatMap(GenerateStateMachineDiagramsTask::getDiagramNameProperty)
                    .orElse(method.getNameAsString());
            var transitions = diagrams.computeIfAbsent(diagramName, k -> new ArrayList<>());

            method.findAll(MethodCallExpr.class).forEach(call -> {
                if (call.getNameAsString().equals("setInitialState")) {
                    initialStates.put(diagramName, call.getArguments().getFirst().orElseThrow().toString());
                    return;
                }

                boolean isWhen = call.getNameAsString().equals("when");
                boolean isWhenComplete = call.getNameAsString().equals("whenComplete");
                boolean isWhenCompleteAnd = call.getNameAsString().equals("whenCompleteAnd");
                if (!isWhen && !isWhenComplete && !isWhenCompleteAnd) return;

                var toStateCallOpt = call.getScope()
                        .filter(s -> s instanceof MethodCallExpr)
                        .map(s -> ((MethodCallExpr) s));
                if (toStateCallOpt.isEmpty()) return;

                var transitionCondExpr = call.getArguments().getFirst();
                var transitionCond = "";
                if (isWhenComplete) {
                    transitionCond = "when complete";
                } else {
                    var prefix = isWhen ? "when " : "when complete and ";
                    var expr = transitionCondExpr.orElseThrow().toString();
                    expr = expr.replace("() -> ", "");
                    expr = expr.replace(".getAsBoolean()", "");
                    System.out.println(expr);
                    if (expr.contains("||") || expr.contains(".or(")) {
                        expr = "(" + expr + ")";
                    }
                    transitionCond = prefix + expr;
                }

                var toStateCall = toStateCallOpt.get();
                var toState = toStateCall.getArguments().getFirst().orElseThrow();
                if (toStateCall.getNameAsString().equals("switchTo")) {
                    var fromState = toStateCall.getScope().orElseThrow().toString();
                    if (toState.isLambdaExpr()) {
                        transitions.addAll(
                            transitionsFromLambdaExpr(returnAnalyzer, toState, fromState, transitionCond)
                        );
                    } else {
                        transitions.add(new Transition(fromState, toState.toString(), transitionCond));
                    }
                } else if (toStateCall.getNameAsString().equals("to")) {
                    // If it's just a regular "to", there must be a switchFromAny before it.
                    var argList = toStateCall.getScope()
                            .filter(s -> s instanceof MethodCallExpr)
                            .map(s -> ((MethodCallExpr) s))
                            .map(MethodCallExpr::getArguments);
                    if (argList.isEmpty()) return;
                    for (var fromState: argList.get()) {
                        if (toState.isLambdaExpr()) {
                            transitions.addAll(
                                transitionsFromLambdaExpr(returnAnalyzer, toState, fromState.toString(), transitionCond)
                            );
                        } else {
                            var t = new Transition(fromState.toString(), toState.toString(), transitionCond);
                            transitions.add(t);
                        }
                    }
                }
            });
        });
    }

    private static List<Transition> transitionsFromLambdaExpr(
        ReturnConditionAnalyzer returnAnalyzer,
        Expression toState,
        String fromState,
        String transitionCond
    ) {
        var transitions = new ArrayList<Transition>();
        for (var entry: returnAnalyzer.analyze(toState.asLambdaExpr()).entrySet()) {
            var innerToState = entry.getKey();
            var additionalCond = entry.getValue();
            if (additionalCond.contains("||") || additionalCond.contains(".or(")) {
                additionalCond = "(" + additionalCond + ")";
            }
            var fullCond = transitionCond + " and " + additionalCond;
            transitions.add(new Transition(fromState, innerToState, fullCond));
        }
        return transitions;
    }

    private static java.util.Optional<String> getDiagramNameProperty(NormalAnnotationExpr expr) {
        return expr.getPairs().stream()
            .filter(p -> p.getNameAsString().equals("diagramName"))
            .findFirst()
            .map(p -> p.getValue().asStringLiteralExpr().asString())
            .filter(s -> !s.isEmpty());
    }

    private static void extractFromDirectory(String rootDir) throws IOException {
        Map<String, List<Transition>> diagrams = new LinkedHashMap<>();
        Map<String, String> initialStates = new HashMap<>();

        Files.walk(Paths.get(rootDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        extractFromFile(path.toFile(), diagrams, initialStates);
                    } catch (IOException e) {
                        System.err.println("Failed to parse: " + path + " — " + e.getMessage());
                    }
                });

        for (var entry : diagrams.entrySet()) {
            var methodName = entry.getKey();
            var transitions = entry.getValue();
            var file = new File("diagrams/" + methodName + ".mermaid");
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (var writer = new FileWriter(file)) {
                writer.write(generateMermaid(transitions, initialStates.get(methodName)));
            }
        }
    }

    private static String generateMermaid(List<Transition> transitions, String initialState) {
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
        if (initialState != null) {
            sb.append("\n    classDef initialState color:#00FF00\n")
                .append("    class ")
                .append(initialState)
                .append(" initialState");
        }
        return sb.toString();
    }

    /** Strips characters that break Mermaid identifiers/labels */
    private static String sanitize(String s) {
        return s.replaceAll("[\"'\\n\\r]", "").trim();
    }
}
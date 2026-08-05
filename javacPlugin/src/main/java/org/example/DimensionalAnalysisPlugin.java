package org.example;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;

/**
 * Javac compiler plugin for compile-time dimensional analysis.
 */
public class DimensionalAnalysisPlugin implements Plugin {
    public static final String NAME = "DimensionalAnalysisPlugin";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        Trees trees = Trees.instance(task);
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                // No action needed on start
            }

            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                    DimensionalAnalysisVisitor visitor = new DimensionalAnalysisVisitor(trees);
                    visitor.scanCompilationUnit(e.getCompilationUnit());
                }
            }
        });
    }
}

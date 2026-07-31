package org.example;

import com.sun.source.util.JavacTask;

public class MyPlugin extends DimensionalAnalysisPlugin {
    @Override
    public String getName() {
        return "MyPlugin";
    }

    @Override
    public void init(JavacTask task, String... args) {
        super.init(task, args);
    }
}

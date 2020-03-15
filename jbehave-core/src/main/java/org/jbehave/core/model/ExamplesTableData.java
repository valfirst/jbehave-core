package org.jbehave.core.model;

import java.util.Deque;

class ExamplesTableData {
    private final String table;
    private final Deque<ExamplesTableProperties> properties;

    ExamplesTableData(String table, Deque<ExamplesTableProperties> properties) {
        this.table = table;
        this.properties = properties;
    }

    String getTable() {
        return table;
    }

    Deque<ExamplesTableProperties> getProperties() {
        return properties;
    }
}

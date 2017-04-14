package org.jbehave.core.model;

import java.util.Collections;

import org.jbehave.core.failures.UUIDExceptionWrapper;

public class FailedStory extends Story {

    private Throwable cause;

    public FailedStory(String path, Meta meta, String stage, String subStage, Throwable cause) {
        this(path, meta, new Scenario(stage, Collections.singletonList(subStage)), cause);
    }

    private FailedStory(String path, Meta meta, Scenario stage, Throwable cause) {
        super(path, Description.EMPTY, meta, Narrative.EMPTY, Collections.singletonList(stage));
        this.cause = new UUIDExceptionWrapper(cause);
    }

    public Throwable getCause() {
        return cause;
    }

    public String getStage() {
        return getScenario().getTitle();
    }

    public String getSubStage() {
        return getScenario().getSteps().get(0);
    }

    private Scenario getScenario() {
        return getScenarios().get(0);
    }
}

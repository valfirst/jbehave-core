package org.jbehave.core.model;

import java.util.List;

public class StepWithOutcome
{
    private String uuid;
    private String parentUuid;
    private String title;
    private String outcome;
    private Throwable storyFailure;
    private Throwable cause;
    private List<StepWithOutcome> steps;
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome)
    {
        this.outcome = outcome;
    }

    public List<StepWithOutcome> getSteps() {
        return steps;
    }

    public void setSteps(List<StepWithOutcome> steps)
    {
        this.steps = steps;
    }

    public String getParentUuid() {
        return parentUuid;
    }

    public void setParentUuid(String parentUuid)
    {
        this.parentUuid = parentUuid;
    }

    public Throwable getStoryFailure() {
        return storyFailure;
    }

    public void setStoryFailure(Throwable storyFailure) {
        this.storyFailure = storyFailure;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public void addStep(StepWithOutcome stepWithOutcome) {
        this.steps.add(stepWithOutcome);
    }
}

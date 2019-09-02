package org.jbehave.core.model;

import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Scenario {

    private final String title;
    private final Meta meta;
    private final GivenStories givenStories;
    private final ExamplesTable examplesTable;
    private final List<String> steps;
    private final List<String> rawSteps;

    public Scenario() {
        this(Arrays.<String>asList());
    }

    public Scenario(List<String> steps) {
        this(null, steps);
    }

    public Scenario(String title, Meta meta) {
        this(title, meta, null, null, Arrays.<String>asList());
    }

    public Scenario(String title, List<String> steps) {
        this(title, null, null, null, steps);
    }

    public Scenario(String title, Meta meta, GivenStories givenStories, ExamplesTable examplesTable, List<String> steps) {
        this(title, meta, givenStories, examplesTable, steps, null);
    }

    public Scenario(String title, Meta meta, GivenStories givenStories, ExamplesTable examplesTable, List<String> steps, List<String> rawSteps) {
        this.title = title;
        this.meta = meta;
        this.givenStories = givenStories;
        this.examplesTable = examplesTable;
        this.steps = steps;
        this.rawSteps = rawSteps;
    }
    
    public String getTitle() {
        if ( title == null ){
            return EMPTY;
        }
        return title;
    }

    public boolean hasGivenStories() {
        return givenStories != null;
    }

    public GivenStories getGivenStories() {
        if ( !hasGivenStories() ){
            return GivenStories.EMPTY;
        }
        return givenStories;
    }

    public boolean hasExamplesTable() {
        return examplesTable != null;
    }

    public ExamplesTable getExamplesTable() {
        if ( !hasExamplesTable() ){
            return ExamplesTable.EMPTY;
        }
        return examplesTable;
    }

    public boolean hasMeta(){
        return meta != null;
    }

    public Meta getMeta(){
        if ( !hasMeta() ){
            return Meta.EMPTY;
        }
        return meta;
    }

    public Meta asMeta(String prefix){
        Properties p = new Properties();
        p.setProperty(prefix+"title", getTitle());
        p.setProperty(prefix+"givenStories", getGivenStories().asString());
        p.setProperty(prefix+"examplesTable", getExamplesTable().asString());
        return new Meta(p);
    }

    public List<String> getSteps() {
        return unmodifiableList(steps);
    }

    public List<String> getRawSteps() {
        return rawSteps == null ? null : unmodifiableList(rawSteps);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}

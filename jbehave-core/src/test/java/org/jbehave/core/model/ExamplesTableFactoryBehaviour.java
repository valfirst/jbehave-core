package org.jbehave.core.model;

import org.jbehave.core.i18n.LocalizedKeywords;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.ResourceLoader;
import org.jbehave.core.steps.ParameterConverters;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.equalTo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExamplesTableFactoryBehaviour {

    private String tableAsString = "|one|two|\n|11|22|\n";

    @Test
    public void shouldCreateExamplesTableFromTableInput() {
        // Given
        ExamplesTableFactory factory = newExamplesTableFactory(new LoadFromClasspath());
        
        // When        
        ExamplesTable examplesTable = factory.createExamplesTable(tableAsString);
        
        // Then
        assertThat(examplesTable.asString(), equalTo(tableAsString));
    }

    @Test
    public void shouldCreateExamplesTableFromResourceInput() {
        // Given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ExamplesTableFactory factory = newExamplesTableFactory(resourceLoader);
        
        // When
        String resourcePath = "/path/to/table";
        when(resourceLoader.loadResourceAsText(resourcePath)).thenReturn(tableAsString);
        ExamplesTable examplesTable = factory.createExamplesTable(resourcePath);
        
        // Then
        assertThat(examplesTable.asString(), equalTo(tableAsString));
    }

    private ExamplesTableFactory newExamplesTableFactory(ResourceLoader resourceLoader)
    {
        return new ExamplesTableFactory(new LocalizedKeywords(), resourceLoader, new ParameterConverters());
    }
}

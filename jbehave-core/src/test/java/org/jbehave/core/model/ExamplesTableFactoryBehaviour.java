package org.jbehave.core.model;

import org.jbehave.core.i18n.LocalizedKeywords;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.ResourceLoader;
import org.jbehave.core.steps.ParameterControls;
import org.jbehave.core.steps.ParameterConverters;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.equalTo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExamplesTableFactoryBehaviour {

    private static final String TABLE_AS_STRING = "|one|two|\n|11|22|\n";

    private static final String TABLE_WITH_INLINED_SEPARATTORS =
            "{ignorableSeparator=!--,headerSeparator=!,valueSeparator=!,commentSeparator=#}\n"
            + "!header 1!header 2! .... !header n!\n"
            + "!-- An ignored row --!\n"
            + "!value 11#comment in value!value 12! .... !value 1n!\n"
            + "!-- Another ignored row --!\n"
            + "!value m1!value m2! .... !value mn!\n";

    private static final String FILTERED_TABLE_WITH_INLINED_SEPARATTORS =
            "{ignorableSeparator=!--,headerSeparator=!,valueSeparator=!,commentSeparator=#}\n"
            + "!header 1!header 2!....!header n!\n"
            + "!value 11!value 12!....!value 1n!\n"
            + "!value m1!value m2!....!value mn!\n";

    @Test
    public void shouldCreateExamplesTableFromTableInput() {
        // Given
        ExamplesTableFactory factory = newExamplesTableFactory(new LoadFromClasspath());
        
        // When        
        ExamplesTable examplesTable = factory.createExamplesTable(TABLE_AS_STRING);
        
        // Then
        assertThat(examplesTable.asString(), equalTo(TABLE_AS_STRING));
    }

    @Test
    public void shouldCreateExamplesTableFromResourceInput() {
        // Given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ExamplesTableFactory factory = newExamplesTableFactory(resourceLoader);
        
        // When
        String resourcePath = "/path/to/table";
        when(resourceLoader.loadResourceAsText(resourcePath)).thenReturn(TABLE_AS_STRING);
        ExamplesTable examplesTable = factory.createExamplesTable(resourcePath);
        
        // Then
        assertThat(examplesTable.asString(), equalTo(TABLE_AS_STRING));
    }

    @Test
    public void shouldCreateExamplesTableFromTableInputWithInlinedSeparators() {
        // Given
        ExamplesTableFactory factory = newExamplesTableFactory(new LoadFromClasspath());

        // When
        ExamplesTable examplesTable = factory.createExamplesTable(TABLE_WITH_INLINED_SEPARATTORS);

        // Then
        assertThat(examplesTable.asString(), equalTo(FILTERED_TABLE_WITH_INLINED_SEPARATTORS));
    }

    private ExamplesTableFactory newExamplesTableFactory(ResourceLoader resourceLoader)
    {
        return new ExamplesTableFactory(new LocalizedKeywords(), resourceLoader, new ParameterConverters(),
                new ParameterControls(), new TableTransformers());
    }
}

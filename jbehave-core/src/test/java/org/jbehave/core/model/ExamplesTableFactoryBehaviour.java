package org.jbehave.core.model;

import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.ResourceLoader;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.equalTo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExamplesTableFactoryBehaviour {

    private static final String TABLE_AS_STRING = "|one|two|\n|11|22|\n";

    private static final String RESOURCE_PATH = "/path/to/table";

    private static final String PROPERTIES = "{ignorableSeparator=!--,headerSeparator=!,valueSeparator=!,commentSeparator=#}\n";

    private static final String TABLE_WITH_INLINED_SEPARATTORS = PROPERTIES
            + "!header 1!header 2! .... !header n!\n"
            + "!-- An ignored row --!\n"
            + "!value 11#comment in value!value 12! .... !value 1n!\n"
            + "!-- Another ignored row --!\n"
            + "!value m1!value m2! .... !value mn!\n";

    private static final String FILTERED_TABLE_WITH_INLINED_SEPARATTORS = PROPERTIES
            + "!header 1!header 2!....!header n!\n"
            + "!value 11!value 12!....!value 1n!\n"
            + "!value m1!value m2!....!value mn!\n";

    @Test
    public void shouldCreateExamplesTableFromTableInput() {
        // Given
        ExamplesTableFactory factory = new ExamplesTableFactory(new LoadFromClasspath(), new TableTransformers());
        
        // When        
        ExamplesTable examplesTable = factory.createExamplesTable(TABLE_AS_STRING);
        
        // Then
        assertThat(examplesTable.asString(), equalTo(TABLE_AS_STRING));
    }

    @Test
    public void shouldCreateExamplesTableFromResourceInput() {
        // Given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ExamplesTableFactory factory = new ExamplesTableFactory(resourceLoader, new TableTransformers());
        
        // When
        when(resourceLoader.loadResourceAsText(RESOURCE_PATH)).thenReturn(TABLE_AS_STRING);
        ExamplesTable examplesTable = factory.createExamplesTable(RESOURCE_PATH);
        
        // Then
        assertThat(examplesTable.asString(), equalTo(TABLE_AS_STRING));
    }

    @Test
    public void shouldCreateExamplesTableWithParametersFromResourceInput() {
        // Given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ExamplesTableFactory factory = new ExamplesTableFactory(resourceLoader, new TableTransformers());

        // When
        when(resourceLoader.loadResourceAsText(RESOURCE_PATH)).thenReturn(TABLE_AS_STRING);
        ExamplesTable examplesTable = factory.createExamplesTable(PROPERTIES + RESOURCE_PATH);

        // Then
        assertThat(examplesTable.asString(), equalTo(PROPERTIES + TABLE_AS_STRING));
    }

    @Test
    public void shouldCreateExamplesTableFromResourceInputWithPreceedingLineBreaks() {
        // Given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ExamplesTableFactory factory = new ExamplesTableFactory(resourceLoader, new TableTransformers());
        
        // When
        when(resourceLoader.loadResourceAsText(RESOURCE_PATH)).thenReturn(TABLE_AS_STRING);
        ExamplesTable examplesTable = factory.createExamplesTable("\n\n\n/path/to/table");
        
        // Then
        assertThat(examplesTable.asString(), equalTo(TABLE_AS_STRING));
    }

    @Test
    public void shouldCreateExamplesTableFromTableInputWithInlinedSeparators() {
        // Given
        ExamplesTableFactory factory = new ExamplesTableFactory(new LoadFromClasspath(), new TableTransformers());

        // When
        ExamplesTable examplesTable = factory.createExamplesTable(TABLE_WITH_INLINED_SEPARATTORS);

        // Then
        assertThat(examplesTable.asString(), equalTo(FILTERED_TABLE_WITH_INLINED_SEPARATTORS));
    }

    @Test
    public void shouldCreateExamplesTableFromTableInputWithInlinedSeparatorsHavingSpacesAtStart() {
        // Given
        ExamplesTableFactory factory = new ExamplesTableFactory(new LoadFromClasspath(), new TableTransformers());

        // When
        ExamplesTable examplesTable = factory.createExamplesTable(" " + TABLE_WITH_INLINED_SEPARATTORS);

        // Then
        assertThat(examplesTable.asString(), equalTo(FILTERED_TABLE_WITH_INLINED_SEPARATTORS));
    }
}

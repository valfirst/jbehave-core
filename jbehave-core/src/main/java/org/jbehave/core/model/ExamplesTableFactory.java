package org.jbehave.core.model;

import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.io.ResourceLoader;
import org.jbehave.core.steps.ParameterControls;
import org.jbehave.core.steps.ParameterConverters;

import static java.util.regex.Pattern.DOTALL;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory that creates instances of ExamplesTable from different type of
 * inputs:
 * <ul>
 * <li>table text input, i.e. any input that contains a
 * {@link Keywords#examplesTableHeaderSeparator()}</li>
 * <li>resource path input, the table as text is loaded via the
 * {@link ResourceLoader}.</li>
 * </ul>
 * Factory also supports optional specification of {@link ParameterConverters}
 * to allow the ExamplesTable to convert row values.
 * <p>
 * <b>NOTE</b>: Users needing parameter conversion in the ExamplesTable, i.e.
 * invoking {@link ExamplesTable#getRowAsParameters(int)}, will need to use a
 * factory constructor providing explicitly the ParameterConverters instance
 * configured in the
 * {@link Configuration#useParameterConverters(ParameterConverters)}.
 * </p>
 */
public class ExamplesTableFactory {

    public static final Pattern INLINED_PROPERTIES_PATTERN = Pattern.compile("\\{(.*?)\\}\\s*(.*)", DOTALL);
    public static final ExamplesTable EMPTY = new ExamplesTable("");

    private String propertiesAsString = "";
    private Keywords keywords;
    private final ResourceLoader resourceLoader;
    private final ParameterConverters parameterConverters;
    private final ParameterControls parameterControls;
    private final TableTransformers tableTransformers;
    private ExamplesTableProperties properties;

    public ExamplesTableFactory(Keywords keywords, ResourceLoader resourceLoader,
            ParameterConverters parameterConverters, ParameterControls parameterControls) {
        this(keywords, resourceLoader, parameterConverters, parameterControls, new TableTransformers());
    }

    public ExamplesTableFactory(Keywords keywords, ResourceLoader resourceLoader,
            ParameterConverters parameterConverters, ParameterControls parameterControls,
            TableTransformers tableTransformers) {
        this.keywords = keywords;
        this.resourceLoader = resourceLoader;
        this.parameterConverters = parameterConverters;
        this.parameterControls = parameterControls;
        this.tableTransformers = tableTransformers;
    }

    public ExamplesTable createExamplesTable(String input) {
        String tableAsString;
		if (isBlank(input) || isTable(input)) {
            tableAsString = input;
        } else {
            tableAsString = resourceLoader.loadResourceAsText(input);
        }

		tableAsString = parse(input, keywords.examplesTableHeaderSeparator(),
				keywords.examplesTableValueSeparator(), keywords.examplesTableIgnorableSeparator());
        String transformer = properties.getTransformer();

        if (transformer != null) {
            tableAsString = tableTransformers.transform(transformer, tableAsString, properties);
        }
        return new ExamplesTable(tableAsString, properties.getHeaderSeparator(),
        		properties.getValueSeparator(), properties.getIgnorableSeparator(),
                parameterConverters, parameterControls);
    }


    private String parse(String tableAsString, String headerSeparator, String valueSeparator, String ignorableSeparator) {
        String tableWithoutProperties = stripProperties(tableAsString.trim());
    	Matcher matcher = INLINED_PROPERTIES_PATTERN.matcher(tableAsString);
        if (matcher.matches()) {
            propertiesAsString = matcher.group(1);
        }
        properties = new ExamplesTableProperties(propertiesAsString, headerSeparator, valueSeparator, ignorableSeparator);
        return tableWithoutProperties;
    }
    
    private String stripProperties(String tableAsString) {
        Matcher matcher = INLINED_PROPERTIES_PATTERN.matcher(tableAsString);
        if (matcher.matches()) {
            propertiesAsString = matcher.group(1);
            return matcher.group(2);
        }
        return tableAsString;
    }
    
    protected boolean isTable(String input) {
        return input.startsWith(keywords.examplesTableHeaderSeparator())
                || ExamplesTable.INLINED_PROPERTIES_PATTERN.matcher(input).matches();
    }
}

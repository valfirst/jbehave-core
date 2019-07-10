package org.jbehave.core.steps.context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jbehave.core.annotations.ToContext;
import org.jbehave.core.reporters.NullStoryReporter;

/**
 * Holds runtime context-related objects.
 */
public class StepsContext extends NullStoryReporter {

    private static final String OBJECT_ALREADY_STORED_MESSAGE = "Object key '%s' has been already stored before.";
    private static final String OBJECT_NOT_STORED_MESSAGE = "Object key '%s' has not been stored";

	private static final ThreadLocal<Map<String, Object>> exampleObjects = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> scenarioObjects = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> storyObjects = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> keysStored = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> compositeObjects = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<AtomicInteger> compositeCounter = ThreadLocal.withInitial(AtomicInteger::new);

    public void put(String key, Object object, ToContext.RetentionLevel retentionLevel) {
        checkForDuplicate(key);
        Map<String, Object> objects;
        if (ToContext.RetentionLevel.EXAMPLE.equals(retentionLevel)) {
            objects = getExampleObjects();
        } else if (ToContext.RetentionLevel.SCENARIO.equals(retentionLevel)) {
            objects = getScenarioObjects();
        } else {
            objects = getStoryObjects();
        }
        objects.put(key, object);
    }

    public void putCompositeObject(String key, Object object) {
        compositeObjects.get().put(key, object);
    }

    public Object getCompositeObject(String key) {
        return compositeObjects.get().get(key);
    }

    @Override
    public void beforeStep(String step) {
        compositeCounter.get().incrementAndGet();
    }

    @Override
    public void successful(String step) {
        stopStep();
    }

    @Override
    public void failed(String step, Throwable cause) {
        stopStep();
    }

    @Override
    public void ignorable(String step) {
        stopStep();
    }

    @Override
    public void pending(String step) {
        // Pending steps don't report 'beforeStep'
    }

    private void stopStep() {
        // Before/After steps may report failure without prior 'beforeStep' invocation,
        // so we must be sure counter doesn't become negative
        AtomicInteger counter = compositeCounter.get();
        if (counter.get() == 0 || counter.decrementAndGet() == 0) {
            compositeObjects.get().clear();
        }
    }

    private void checkForDuplicate(String key) {
        Set<String> keys = keysStored.get();
        if (keys.contains(key)) {
            throw new ObjectAlreadyStoredException(String.format(OBJECT_ALREADY_STORED_MESSAGE, key));
        } else {
            keys.add(key);
        }
    }

    public Object get(String key) {
        Object object = getExampleObjects().get(key);
        if (object == null) {
            object = getScenarioObjects().get(key);
            if (object == null) {
                object = getStoryObjects().get(key);
            }
        }

        if (object == null) {
            throw new ObjectNotStoredException(String.format(OBJECT_NOT_STORED_MESSAGE, key));
        }

        return object;
    }

    private Map<String, Object> getExampleObjects() {
        Map<String, Object> objects = exampleObjects.get();
        if (objects == null) {
            objects = new HashMap<>();
            exampleObjects.set(objects);
        }
        return objects;
    }

    private Map<String, Object> getScenarioObjects() {
        Map<String, Object> objects = scenarioObjects.get();
        if (objects == null) {
            objects = new HashMap<>();
            scenarioObjects.set(objects);
        }
        return objects;
    }

    private Map<String, Object> getStoryObjects() {
        Map<String, Object> objects = storyObjects.get();
        if (objects == null) {
            objects = new HashMap<>();
            storyObjects.set(objects);
        }
        return objects;
    }

    private Set<String> getKeys() {
        Set<String> keys = keysStored.get();
        if (keys == null) {
            keys = new HashSet<>();
            keysStored.set(keys);
        }
        return keys;
    }

    public void resetExample() {
        Set<String> keys = getKeys();
        keys.removeAll(getExampleObjects().keySet());
        exampleObjects.set(new HashMap<String, Object>());
    }

    public void resetScenario() {
        Set<String> keys = getKeys();
        keys.removeAll(getScenarioObjects().keySet());
        scenarioObjects.set(new HashMap<String, Object>());
    }

    public void resetStory() {
        storyObjects.set(new HashMap<String, Object>());
        keysStored.set(new HashSet<String>());
    }

    @SuppressWarnings("serial")
	public static class ObjectNotStoredException extends RuntimeException {

        public ObjectNotStoredException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("serial")
	public static class ObjectAlreadyStoredException extends RuntimeException {

        public ObjectAlreadyStoredException(String message) {
            super(message);
        }
    }

}

package cmr.elasticsearch.plugins;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.DocReader;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.FilterScript;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.search.lookup.LeafSearchLookup;
import org.elasticsearch.search.lookup.LeafStoredFieldsLookup;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cmr.spatial.serialize.OrdsInfoShapes;
import cmr.spatial.relations.ShapeIntersections;
import cmr.spatial.relations.ShapePredicate;
import cmr.spatial.shape.SpatialShape;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure Java Elasticsearch plugin for CMR spatial searches.
 * Uses the Java spatial library to avoid Clojure runtime security issues in ES8+.
 */
public class SpatialSearchPlugin extends Plugin implements ScriptPlugin {

    public SpatialSearchPlugin(Settings settings) {}

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new SpatialScriptEngine();
    }

    /**
     * Script engine implementation for spatial searches.
     */
    private static class SpatialScriptEngine implements ScriptEngine {
        private static final Logger logger = LogManager.getLogger(SpatialScriptEngine.class);

        @Override
        public String getType() {
            return "cmr_spatial";
        }

        @Override
        public <T> T compile(String scriptName, String source, ScriptContext<T> context, Map<String, String> params) {
            if (!context.instanceClazz.equals(FilterScript.class)) {
                throw new IllegalArgumentException(
                    getType() + " scripts cannot be used for context [" + context.name + "]");
            }

            if (!source.equals("spatial")) {
                throw new IllegalArgumentException("Unknown script name [" + source + "]");
            }

            return context.factoryClazz.cast(new SpatialScriptFactory());
        }

        @Override
        public Set<ScriptContext<?>> getSupportedContexts() {
            return Set.of(FilterScript.CONTEXT);
        }
    }

    /**
     * Factory for creating leaf factories with parsed spatial parameters.
     */
    private static class SpatialScriptFactory implements FilterScript.Factory {
        private static final Logger logger = LogManager.getLogger(SpatialScriptFactory.class);

        @Override
        public boolean isResultDeterministic() {
            return false;
        }

        @Override
        public FilterScript.LeafFactory newFactory(Map<String, Object> params, SearchLookup lookup) {
            // Extract and validate parameters
            String ordsParam = XContentMapValues.nodeStringValue(params.get("ords"), null);
            String ordsInfoParam = XContentMapValues.nodeStringValue(params.get("ords-info"), null);

            if (ordsParam == null || ordsInfoParam == null) {
                throw new IllegalArgumentException(
                    "Missing required parameters: ords and ords-info must be provided");
            }

            // Parse comma-separated integers
            List<Integer> ords = parseIntList(ordsParam);
            List<Integer> ordsInfo = parseIntList(ordsInfoParam);

            // Convert to spatial shape and create intersects predicate
            try {
                List<SpatialShape> shapes = OrdsInfoShapes.ordsInfoToShapes(ordsInfo, ords);
                if (shapes.isEmpty()) {
                    throw new IllegalArgumentException("No shapes could be parsed from ords-info/ords");
                }
                
                SpatialShape queryShape = shapes.get(0);
                ShapePredicate intersectsFn = ShapeIntersections.createIntersectsFn(queryShape);

                return new SpatialLeafFactory(intersectsFn, lookup);

            } catch (Exception e) {
                logger.error("Failed to create intersects function from shape parameters", e);
                throw new IllegalArgumentException("Unable to create spatial intersects function", e);
            }
        }

        private static List<Integer> parseIntList(String csvString) {
            String[] parts = csvString.split(",");
            return java.util.Arrays.stream(parts)
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        }
    }

    /**
     * Leaf factory that creates FilterScript instances per segment.
     */
    private static class SpatialLeafFactory implements FilterScript.LeafFactory {
        private final ShapePredicate intersectsFn;
        private final SearchLookup lookup;

        public SpatialLeafFactory(ShapePredicate intersectsFn, SearchLookup lookup) {
            this.intersectsFn = intersectsFn;
            this.lookup = lookup;
        }

        @Override
        public FilterScript newInstance(DocReader reader) throws IOException {
            return new SpatialScript(intersectsFn, lookup, reader);
        }
    }

    /**
     * FilterScript that executes spatial intersection tests on documents.
     */
    private static class SpatialScript extends FilterScript {
        private static final Logger logger = LogManager.getLogger(SpatialScript.class);

        private final ShapePredicate intersectsFn;
        private final SearchLookup searchLookup;
        private LeafSearchLookup leafLookup;

        public SpatialScript(ShapePredicate intersectsFn, SearchLookup lookup, DocReader reader) {
            super(Map.of(), lookup, reader);
            this.intersectsFn = intersectsFn;
            this.searchLookup = lookup;
            // Try to get leaf lookup - DocReader should be associated with a LeafReaderContext
            try {
                // In ES 8, DocReader provides the context
                Object ctx = reader.getClass().getMethod("getLeafReaderContext").invoke(reader);
                if (ctx instanceof org.apache.lucene.index.LeafReaderContext) {
                    this.leafLookup = lookup.getLeafSearchLookup((org.apache.lucene.index.LeafReaderContext) ctx);
                }
            } catch (Exception e) {
                logger.warn("Could not get leaf lookup from reader", e);
            }
        }

        @Override
        public void setDocument(int docId) {
            super.setDocument(docId);
            // Update the leaf lookup for this document
            if (leafLookup != null) {
                leafLookup.setDocument(docId);
            }
        }

        @Override
        public boolean execute() {
            try {
                if (leafLookup == null) {
                    logger.warn("Leaf lookup not initialized");
                    return false;
                }

                // Access fields through the leaf lookup
                LeafStoredFieldsLookup fields = leafLookup.fields();
                
                if (fields == null) {
                    logger.debug("Could not access fields lookup");
                    return false;
                }

                // Extract ords-info and ords from document fields
                List<?> ordsInfo = getFieldValues(fields, "ords-info");
                List<?> ords = getFieldValues(fields, "ords");

                if (ordsInfo == null || ords == null) {
                    return false;
                }

                // Deserialize shapes from document
                List<SpatialShape> docShapes = OrdsInfoShapes.ordsInfoToShapes(ordsInfo, ords);

                // Test if any document shape intersects the query shape
                for (SpatialShape docShape : docShapes) {
                    if (intersectsFn.intersects(docShape)) {
                        return true;
                    }
                }

                return false;

            } catch (Exception e) {
                logger.error("Error executing spatial script", e);
                throw new RuntimeException("Spatial intersection test failed", e);
            }
        }

        private LeafStoredFieldsLookup getFieldsLookup() {
            try {
                // Access the SearchLookup to get fields
                if (searchLookup != null) {
                    // ES 7/8 API: searchLookup should give us access to leaf lookup
                    java.lang.reflect.Method getLeafSearchLookup = 
                        searchLookup.getClass().getMethod("getLeafSearchLookup", org.apache.lucene.index.LeafReaderContext.class);
                    // Get the reader context from our DocReader (which wraps it)
                    // For now, try to get it from the FilterScript base
                    return null; // Will fix this properly
                }
            } catch (Exception e) {
                logger.warn("Could not access leaf lookup", e);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private List<?> getFieldValues(LeafStoredFieldsLookup fields, String key) {
            if (fields == null || key == null) {
                return null;
            }
            
            try {
                if (!fields.containsKey(key)) {
                    return null;
                }
                
                Object fieldLookup = fields.get(key);
                if (fieldLookup == null) {
                    return null;
                }

                // FieldLookup should have a getValues() method
                java.lang.reflect.Method getValues = fieldLookup.getClass().getMethod("getValues");
                Object values = getValues.invoke(fieldLookup);
                if (values instanceof List) {
                    return (List<?>) values;
                }
            } catch (Exception e) {
                logger.debug("Could not access field values for key: " + key, e);
            }

            return null;
        }
    }
}

# schema-validation-lib

JSON Schema validation library.

## Gen-class functions available for use:

- `parseSchemaFromString(String schemaToParse) -> org.everit.json.schema.Schema`: Parse JSON Schema from string.
- `parseSchemaFromURI(String URI) -> org.everit.json.schema.Schema`: Parse JSON schema from String URI.
- `parseSchemaFromURI(java.net.URI URI) -> org.everit.json.schema.Schema`: Parse JSON schema from java.net.URI URI.
- `parseSchemaFromPath(String path) -> org.everit.json.schema.Schema`: Parse json schema from path.
Used when schema references require "classpath://" prefix.
- `validateJson(Schema schema, String json) -> java.util.List<String>`: Validate json string based on schema.
Returns list of errors.
- `validateJson(Schema schema, String json, Boolean throw?) -> void throws JSONException, ValidationException`:
Validate json string based on schema.  Throws JSONException on malformed org.json.JSONException on malformed JSON and
org.everit.json.schema.ValidationException on validation errors.

## Example usage:

```
import java.util.List;
import org.everit.json.schema.Schema;
import cmr.validation.jsonSchema;

public class TestValidation {
  public static void main(String[] args) {
    String json = "{\"type\": \"object\", \"additionalProperties\": false, \"properties\": {\"foo\": {\"type\": \"boolean\"}}}";
    Schema schema = jsonSchema.parseSchemaFromString(json);
    String invalidJson = "{\"foo\": 6}";

    /* Without throw (defaults to false) */
    List errors = jsonSchema.validateJson(schema, invalidJson);
    System.out.println(errors);

    /* With throw */
    jsonSchema.validateJson(schema, invalidJson, true);
  }
}
```

```
[#/foo: expected type: Boolean, found: Integer]
Exception in thread "main" org.everit.json.schema.ValidationException: #/foo: expected type: Boolean, found: Integer
... <exception-info> ...
```

## Additional info:
- org.json: http://stleary.github.io/JSON-java/
- org.everit: http://erosb.github.io/everit-json-schema/javadoc/1.11.0/

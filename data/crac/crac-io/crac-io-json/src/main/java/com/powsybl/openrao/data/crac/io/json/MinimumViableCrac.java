package com.powsybl.openrao.data.crac.io.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class MinimumViableCrac {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[1-9]\\d*\\.\\d+$");
    private static final Set<String> REQUIRED_FIELDS = Set.of("type", "version", "id", "name");

    private MinimumViableCrac() {
      //empty
    }

    public static String getFirstValidationError(InputStream cracInputStream) throws IOException {

        Set<String> seenFields = new HashSet<>();
        JsonFactory factory = JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .build();

        try (JsonParser parser = factory.createParser(cracInputStream)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return "Top-level JSON must be an object";
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();

                switch (fieldName) {
                    case "type" -> {
                        String value = parser.getValueAsString();
                        if (!"CRAC".equals(value)) {
                            return "Field 'type' must be 'CRAC'";
                        }
                        seenFields.add("type");
                    }
                    case "version" -> {
                        String value = parser.getValueAsString();
                        if (!VERSION_PATTERN.matcher(value).matches()) {
                            return "Field 'version' must match pattern " + VERSION_PATTERN.pattern();
                        }
                        seenFields.add("version");
                    }
                    case "id" -> {
                        if (!parser.getCurrentToken().isScalarValue()) {
                            return "Field 'id' must be a string";
                        }
                        seenFields.add("id");
                    }
                    case "name" -> {
                        if (!parser.getCurrentToken().isScalarValue()) {
                            return "Field 'name' must be a string";
                        }
                        seenFields.add("name");
                    }
                    default -> parser.skipChildren(); // ignore additionalProperties
                }
            }
        }

        for (String required : REQUIRED_FIELDS) {
            if (!seenFields.contains(required)) {
                return "Missing required field: " + required;
            }
        }

        return null;
    }

}

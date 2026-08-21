package com.fareflow.gtfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Small RFC-4180 reader that also handles UTF-8 BOMs and quoted newlines. */
final class GtfsCsv {

    private GtfsCsv() {
    }

    static void read(InputStream input, Consumer<Map<String, String>> rowConsumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String> headers = readRecord(reader);
            if (headers == null) {
                return;
            }
            if (!headers.isEmpty()) {
                headers.set(0, headers.getFirst().replace("\uFEFF", ""));
            }

            List<String> values;
            while ((values = readRecord(reader)) != null) {
                if (values.size() == 1 && values.getFirst().isBlank()) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index++) {
                    row.put(headers.get(index), index < values.size() ? values.get(index).trim() : "");
                }
                rowConsumer.accept(row);
            }
        }
    }

    private static List<String> readRecord(BufferedReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean sawAny = false;

        while (true) {
            int raw = reader.read();
            if (raw == -1) {
                if (!sawAny && fields.isEmpty() && field.isEmpty()) {
                    return null;
                }
                if (quoted) {
                    throw new IllegalArgumentException("Unclosed quoted GTFS CSV field");
                }
                fields.add(field.toString());
                return fields;
            }
            sawAny = true;
            char character = (char) raw;
            if (character == '"') {
                if (quoted) {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else if (field.isEmpty()) {
                    quoted = true;
                } else {
                    field.append(character);
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else if ((character == '\n' || character == '\r') && !quoted) {
                if (character == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.reset();
                    }
                }
                fields.add(field.toString());
                return fields;
            } else {
                field.append(character);
            }
        }
    }
}

package com.fareflow.gtfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class GtfsArchive implements AutoCloseable {

    private final ZipFile zip;

    GtfsArchive(Path path) throws IOException {
        this.zip = new ZipFile(path.toFile());
    }

    void require(String... names) {
        for (String name : names) {
            if (entry(name) == null) {
                throw new IllegalArgumentException("GTFS archive is missing required file " + name);
            }
        }
        if (entry("calendar.txt") == null && entry("calendar_dates.txt") == null) {
            throw new IllegalArgumentException(
                    "GTFS archive needs calendar.txt or calendar_dates.txt");
        }
    }

    boolean has(String name) {
        return entry(name) != null;
    }

    void rows(String name, Consumer<Map<String, String>> consumer) throws IOException {
        ZipEntry entry = entry(name);
        if (entry == null) {
            return;
        }
        try (InputStream input = zip.getInputStream(entry)) {
            GtfsCsv.read(input, consumer);
        }
    }

    private ZipEntry entry(String name) {
        ZipEntry direct = zip.getEntry(name);
        if (direct != null) {
            return direct;
        }
        return zip.stream()
                .filter(candidate -> !candidate.isDirectory())
                .filter(candidate -> {
                    String value = candidate.getName();
                    int slash = value.lastIndexOf('/');
                    return value.substring(slash + 1).equalsIgnoreCase(name);
                })
                .findFirst().orElse(null);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}

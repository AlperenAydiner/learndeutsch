package com.ichsprechedeutsch.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kucuk CSV okuyucu.
 *
 * Kutuphane eklemek yerine buraya yazildi: ihtiyacimiz tirnakli alanlar,
 * tirnak icinde virgul ve cift tirnakla kacirilmis tirnaktan ibaret.
 * Ilk satir baslik kabul edilir; her satir basliga gore bir Map olur.
 */
final class CsvReader {

    private CsvReader() {
    }

    static List<Map<String, String>> read(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            // BOM varsa at.
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '﻿') {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = splitLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // Tirnak icinde satir sonu varsa devamini oku.
                while (unbalancedQuotes(line)) {
                    String next = reader.readLine();
                    if (next == null) {
                        break;
                    }
                    line = line + "\n" + next;
                }

                List<String> values = splitLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static boolean unbalancedQuotes(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                count++;
            }
        }
        return count % 2 != 0;
    }

    private static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString().trim());
        return out;
    }
}

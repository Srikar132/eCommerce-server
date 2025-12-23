package com.nala.armoire.util; // Adjust package name as needed

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomEsDateDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateString = p.getText();

        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        try {
            // Case 1: The database gave us a full DateTime (e.g., "2025-12-08T10:15:30")
            return LocalDateTime.parse(dateString, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e1) {
            try {
                // Case 2: The database gave us just a Date (e.g., "2025-12-08")
                // We convert it to LocalDateTime by adding "atStartOfDay" (00:00:00)
                return LocalDate.parse(dateString, DATE_FORMATTER).atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new IOException("Failed to parse date: " + dateString, e2);
            }
        }
    }
}
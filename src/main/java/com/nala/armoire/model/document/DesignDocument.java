package com.nala.armoire.model.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.nala.armoire.util.CustomEsDateDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch document for Design entity
 * Optimized for fast searching, filtering, and sorting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "designs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DesignDocument {

    @Id
    private String id;

    // ==================== BASIC FIELDS ====================
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Text)
    private String designImageUrl;

    @Field(type = FieldType.Text)
    private String thumbnailUrl;

    // ==================== DESIGN CATEGORY ====================
    
    @Field(type = FieldType.Keyword)
    private String designCategoryId;

    @Field(type = FieldType.Keyword)
    private String designCategorySlug;

    @Field(type = FieldType.Text)
    private String designCategoryName;

    // ==================== TAGS (For Search) ====================
    
    /**
     * Array of tags for searching
     * e.g., ["floral", "vintage", "summer"]
     */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /**
     * Tags as a single text field for full-text search
     */
    @Field(type = FieldType.Text)
    private String tagsText;

    // ==================== STATUS FLAGS ====================
    
    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Boolean)
    private Boolean isPremium;

    // ==================== STATISTICS ====================
    
    @Field(type = FieldType.Long)
    private Long downloadCount;

    // ==================== TIMESTAMPS ====================
    
    @Field(type = FieldType.Date)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = CustomEsDateDeserializer.class)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = CustomEsDateDeserializer.class)
    private LocalDateTime updatedAt;
}

package com.nala.armoire.model.document;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDocument {

    @Id
    private String id;  // UUID as String for ES

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal basePrice;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Boolean)
    private Boolean isCustomizable;

    @Field(type = FieldType.Keyword)
    private String material;

    @Field(type = FieldType.Text)
    private String careInstructions;

    // Category fields (denormalized for fast filtering)
    @Field(type = FieldType.Keyword)
    private String categorySlug;

    @Field(type = FieldType.Text)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    // Brand fields (denormalized)
    @Field(type = FieldType.Keyword)
    private String brandSlug;

    @Field(type = FieldType.Text)
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String brandId;

    // Variant data (nested for filtering)
    @Field(type = FieldType.Nested)
    private List<VariantInfo> variants;

    // Available sizes (flattened for easy filtering)
    @Field(type = FieldType.Keyword)
    private List<String> availableSizes;

    // Available colors (flattened for easy filtering)
    @Field(type = FieldType.Keyword)
    private List<String> availableColors;

    // Images
    @Field(type = FieldType.Nested)
    private List<ImageInfo> images;

    @Field(type = FieldType.Keyword)
    private String primaryImageUrl;

    // Review data
    @Field(type = FieldType.Double)
    private Double averageRating;

    @Field(type = FieldType.Long)
    private Long reviewCount;

    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Date)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateTimeSerializer.class) // Standard writer
    @JsonDeserialize(using = CustomEsDateDeserializer.class) // YOUR CUSTOM READER
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateTimeSerializer.class) // Standard writer
    @JsonDeserialize(using = CustomEsDateDeserializer.class) // YOUR CUSTOM READER
    private LocalDateTime updatedAt;

    // Nested classes for complex fields
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantInfo {
        @Field(type = FieldType.Keyword)
        private String id;

        @Field(type = FieldType.Keyword)
        private String size;

        @Field(type = FieldType.Keyword)
        private String color;

        @Field(type = FieldType.Keyword)
        private String colorHex;

        @Field(type = FieldType.Integer)
        private Integer stockQuantity;

        @Field(type = FieldType.Double)
        private BigDecimal additionalPrice;

        @Field(type = FieldType.Boolean)
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        @Field(type = FieldType.Keyword)
        private String id;

        @Field(type = FieldType.Keyword)
        private String imageUrl;

        @Field(type = FieldType.Text)
        private String altText;

        @Field(type = FieldType.Integer)
        private Integer displayOrder;

        @Field(type = FieldType.Boolean)
        private Boolean isPrimary;
    }
}
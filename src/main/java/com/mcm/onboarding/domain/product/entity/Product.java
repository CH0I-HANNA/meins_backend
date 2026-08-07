package com.mcm.onboarding.domain.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productName;

    private String modelCode;

    @Column(length = 7)
    private String manufacturedYm; // "YYYY-MM"

    private String material;

    private String color;

    @Column(length = 7)
    private String saleRegisteredYm; // "YYYY-MM"

    private Integer widthCm;

    private Integer depthCm;

    private Integer heightCm;

    private String imageUrl;

    private String thumbnailImageUrl1;

    private String thumbnailImageUrl2;

    private String thumbnailImageUrl3;

    private String productPageUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Product create(String productName, String modelCode, String manufacturedYm, String material,
                                  String color, String saleRegisteredYm, Integer widthCm, Integer depthCm,
                                  Integer heightCm, String imageUrl, String thumbnailImageUrl1,
                                  String thumbnailImageUrl2, String thumbnailImageUrl3, String productPageUrl) {
        Product product = new Product();
        product.productName = productName;
        product.modelCode = modelCode;
        product.manufacturedYm = manufacturedYm;
        product.material = material;
        product.color = color;
        product.saleRegisteredYm = saleRegisteredYm;
        product.widthCm = widthCm;
        product.depthCm = depthCm;
        product.heightCm = heightCm;
        product.imageUrl = imageUrl;
        product.thumbnailImageUrl1 = thumbnailImageUrl1;
        product.thumbnailImageUrl2 = thumbnailImageUrl2;
        product.thumbnailImageUrl3 = thumbnailImageUrl3;
        product.productPageUrl = productPageUrl;
        product.createdAt = LocalDateTime.now();
        return product;
    }
}

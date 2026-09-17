package com.frauddetection.domain;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Merchant")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {

    @Id
    private String merchantId;
    private String name;
    private String category;

    @Builder.Default
    private boolean flagged = false;
}

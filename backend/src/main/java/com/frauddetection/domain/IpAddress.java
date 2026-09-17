package com.frauddetection.domain;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("IpAddress")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpAddress {

    @Id
    private String address;
    private String country;

    @Builder.Default
    private boolean suspicious = false;
}

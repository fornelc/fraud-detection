package com.frauddetection.domain;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    private String accountId;
    private String name;
    private String email;

    @Builder.Default
    private double riskScore = 0.0;

    @Builder.Default
    private boolean blacklisted = false;

    @Relationship(type = "MADE", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();
}

package com.frauddetection.domain;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;

@Node("Transaction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    private String transactionId;
    private double amount;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private boolean flagged = false;

    private String status;

    @Relationship(type = "TO", direction = Relationship.Direction.OUTGOING)
    private Merchant merchant;

    @Relationship(type = "USED", direction = Relationship.Direction.OUTGOING)
    private Device device;

    @Relationship(type = "FROM", direction = Relationship.Direction.OUTGOING)
    private IpAddress ipAddress;
}

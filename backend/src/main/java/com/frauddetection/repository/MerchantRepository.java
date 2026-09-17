package com.frauddetection.repository;

import com.frauddetection.domain.Merchant;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface MerchantRepository extends Neo4jRepository<Merchant, String> {}

package com.frauddetection.repository;

import com.frauddetection.domain.IpAddress;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface IpAddressRepository extends Neo4jRepository<IpAddress, String> {}

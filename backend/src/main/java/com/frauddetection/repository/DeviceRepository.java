package com.frauddetection.repository;

import com.frauddetection.domain.Device;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface DeviceRepository extends Neo4jRepository<Device, String> {}

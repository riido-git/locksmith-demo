package in.riido.locksmith.demo.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

// @Table tells Spring Data this is a JDBC entity; the Redisson starter also puts Spring Data Redis
// on the classpath.
@Table
public record Product(@Id Long id, String sku, String name, int price) {}

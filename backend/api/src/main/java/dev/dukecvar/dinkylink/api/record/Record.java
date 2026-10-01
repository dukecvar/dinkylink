package dev.dukecvar.dinkylink.api.record;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Table("records")
public record Record(
    @Id String shortcode,
    byte[] urlhash,
    String url,
    OffsetDateTime lastTouchedTimestamp
) {}

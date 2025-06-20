package org.ubiety.DTO;

import java.math.BigDecimal;
import java.time.Instant;

public record Orders(String order_id, Instant timestamp, String country, BigDecimal amount) { }

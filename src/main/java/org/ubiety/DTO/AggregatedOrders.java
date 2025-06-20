package org.ubiety.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AggregatedOrders {
    String country;
    String window_start;
    BigDecimal total_amount;
}

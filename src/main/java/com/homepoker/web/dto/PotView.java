package com.homepoker.web.dto;

import java.util.List;

public record PotView(long amount, List<String> eligiblePlayerIds) {
}

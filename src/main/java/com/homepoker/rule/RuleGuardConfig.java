package com.homepoker.rule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * RuleGuard 의존성(정책·시계) 빈 구성. Clock 을 빈으로 두어 운영에선 시스템 시계를,
 * 테스트에선 고정 시계를 주입할 수 있게 한다.
 */
@Configuration
public class RuleGuardConfig {

    @Bean
    public BuyInPolicy buyInPolicy() {
        return BuyInPolicy.defaults();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

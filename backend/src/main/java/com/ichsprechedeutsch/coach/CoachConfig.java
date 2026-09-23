package com.ichsprechedeutsch.coach;

import com.ichsprechedeutsch.config.CoachProperties;
import com.ichsprechedeutsch.config.SrsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hangi koc gerceklestiriminin kullanilacagi tek yerde (SPEC 5.6). Baska bir
 * gerceklestirim (or. AI destekli) eklenirse burada secilir; geri kalan kod
 * yalniz {@link Coach} arayuzunu gorur.
 */
@Configuration
public class CoachConfig {

    @Bean
    Coach coach(CoachProperties config, SrsProperties srs) {
        return new RuleBasedCoach(config, srs);
    }
}

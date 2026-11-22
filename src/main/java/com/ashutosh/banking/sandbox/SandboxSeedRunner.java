package com.ashutosh.banking.sandbox;

import com.ashutosh.banking.repository.CustomerUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** One-shot test fixture setup; never a public money-creation endpoint. */
@Component
@Profile("sandbox-seed")
@ConditionalOnProperty(name = "vaultcore.sandbox.seed-run", havingValue = "true")
public class SandboxSeedRunner implements ApplicationRunner {
    private final SandboxFundingService funding;
    private final CustomerUserRepository users;
    private final ConfigurableApplicationContext context;
    private final String jdbcUrl;
    private final String approval;
    private final String owner;
    private final int pairs;
    private final boolean approvedScale;
    private final String webType;

    public SandboxSeedRunner(SandboxFundingService funding,
                             CustomerUserRepository users,
                             ConfigurableApplicationContext context,
                             @Value("${spring.datasource.url}") String jdbcUrl,
                             @Value("${VAULTCORE_SANDBOX_ACK:}") String approval,
                             @Value("${SANDBOX_OWNER_EMAIL:}") String owner,
                             @Value("${SANDBOX_PAIRS:2}") int pairs,
                             @Value("${SANDBOX_APPROVE_SCALE:false}") boolean approvedScale,
                             @Value("${spring.main.web-application-type:servlet}") String webType) {
        this.funding = funding;
        this.users = users;
        this.context = context;
        this.jdbcUrl = jdbcUrl;
        this.approval = approval;
        this.owner = owner;
        this.pairs = pairs;
        this.approvedScale = approvedScale;
        this.webType = webType;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"I_AGREE_TEST_ONLY".equals(approval) || !"none".equalsIgnoreCase(webType)) {
            throw new IllegalStateException("Sandbox seeding requires explicit approval and a non-web process");
        }
        if (!jdbcUrl.matches("^jdbc:postgresql://[^?#]+/banking_sandbox(?:[?].*)?$")) {
            throw new IllegalStateException("Sandbox seeding requires the dedicated banking_sandbox database");
        }
        if (pairs < 1 || pairs > (approvedScale ? 10000 : 100)) {
            throw new IllegalStateException("Sandbox count must be between 1 and approved maximum");
        }
        if (owner.isBlank() || !users.existsByEmail(owner)) {
            throw new IllegalStateException("Register a synthetic benchmark user before running the seed command");
        }
        funding.ensureTreasury();
        for (int i = 1; i <= pairs; i++) {
            funding.provisionPair(i, owner);
            funding.fundSource(i, owner);
        }
        System.out.printf("SANDBOX_SEEDED pairs=%d amount_per_source=%s TEST UNITS%n",
                pairs, SandboxFundingService.FUNDING_AMOUNT);
        context.close();
    }
}

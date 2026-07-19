package com.runvas.user.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccountPurgeScheduler {

    private final AccountPurgeService accountPurgeService;

    public AccountPurgeScheduler(AccountPurgeService accountPurgeService) {
        this.accountPurgeService = accountPurgeService;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpiredAccounts() {
        accountPurgeService.purgeExpiredAccounts();
    }
}

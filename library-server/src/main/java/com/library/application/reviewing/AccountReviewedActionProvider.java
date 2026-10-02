package com.library.application.reviewing;

import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.identity.contract.AccountChange;
import com.library.identity.contract.AccountCommands;
import com.library.identity.contract.AccountQuery;
import com.library.identity.contract.AccountState;
import com.library.review.contract.ReviewQuery;
import com.library.review.contract.ReviewedAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Maps approved account reviews to a version-checked identity-module update. */
@Component
public final class AccountReviewedActionProvider implements ReviewedActionProvider {

    private static final Set<String> ACCOUNT_ACTIONS = Set.of("SUSPEND", "RESTORE", "CHANGE_ROLE");

    private final AccountQuery accounts;
    private final AccountCommands commands;

    public AccountReviewedActionProvider(AccountQuery accounts, AccountCommands commands) {
        this.accounts = accounts;
        this.commands = commands;
    }

    @Override
    public ReviewedAction actionFor(ReviewQuery.ReviewDetail review) {
        if (!"ACCOUNT_CHANGE".equals(review.targetType()) || !ACCOUNT_ACTIONS.contains(review.action())) {
            return rejectedAction(review);
        }
        return new ReviewedAction() {
            @Override
            public long currentVersion() {
                var current = accounts.accountState(AccountId.of(review.targetId()));
                return current instanceof ModuleResult.Accepted<AccountState> accepted
                        ? accepted.value().version() : -1L;
            }

            @Override
            public ModuleResult<String> execute(Map<String, Object> payload) {
                var result = commands.applyReviewedChange(new AccountCommands.ReviewedAccountChange(
                        AccountId.of(review.targetId()), review.action(), payload, review.targetVersion()));
                if (result instanceof ModuleResult.Rejected<AccountChange> rejected) {
                    return ModuleResult.Rejected.propagate(rejected);
                }
                var change = ((ModuleResult.Accepted<AccountChange>) result).value();
                return ModuleResult.Accepted.of("status=" + change.status() + ";role=" + change.role()
                        + ";version=" + change.version());
            }
        };
    }

    private ReviewedAction rejectedAction(ReviewQuery.ReviewDetail review) {
        return new ReviewedAction() {
            @Override
            public long currentVersion() {
                return review.targetVersion();
            }

            @Override
            public ModuleResult<String> execute(Map<String, Object> payload) {
                return ModuleResult.Rejected.of("MLS-REV-002", "尚未登记的复核动作");
            }
        };
    }
}

package org.tron.core.witness.freeze;

import static org.tron.core.witness.freeze.FreezeStrategy.createFreezeStrategy;

import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.witness.freeze.FreezeStrategy.FreezePolicyContext;
import org.tron.core.witness.freeze.FreezeStrategy.StakeStrategyType;
import org.tron.core.witness.freeze.FreezeStrategy.UnfreezePolicyContext;

@Slf4j
public class FreezeBalanceObject {

  private byte[] account;
  long lastWithdrawTime;
  long lastFreezeTime;
  long freezeBalance;
  long unfreezeBalance;

  private static final FreezeStrategy freezeStrategy;

  static {
    freezeStrategy = createFreezeStrategy(StakeStrategyType.Linear);
  }

  public FreezeBalanceObject() {
  }

  public boolean isFreezeAllowed(FreezePolicyContext context) {
    return freezeStrategy.isFreezeAllowed(this, context);
  }

  //todo: store
  public void freeze(AccountCapsule accountCapsule, FreezePolicyContext context) {
    freezeStrategy.freeze(this, accountCapsule, context);
  }

  public boolean isWithdrawAllowed(UnfreezePolicyContext context) {
    return freezeStrategy.isWithdrawAllowed(this, context);
  }

  public long getAllowedWithdraw(UnfreezePolicyContext context) {
    return freezeStrategy.getAllowedWithdraw(this, context);
  }

  //todo: store
  public void withdraw(AccountCapsule accountCapsule, UnfreezePolicyContext context) {
    freezeStrategy.withdraw(this, accountCapsule, context);
  }


}

package org.tron.core.witness.freeze;

import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.FreezeAccountCapsule;
import org.tron.core.db.AccountStore;
import org.tron.core.db.FreezeAccountStore;

//No freeze in funds
public class FreezeStrategyNoneImpl implements FreezeStrategy {

  @Override
  public boolean isFreezeAllowed(FreezeAccountCapsule fbo, FreezePolicyContext context) {
    return true;
  }

  @Override
  public void freeze(FreezeAccountCapsule fbo, AccountCapsule accountCapsule,
      FreezePolicyContext context, boolean isAccountModified, boolean isFreezeAccountModified) {
    //Direct deposit of account balance
    accountCapsule.setBalance(accountCapsule.getBalance() + context.amount);
  }

  @Override
  public boolean isWithdrawAllowed(FreezeAccountCapsule fbo, UnfreezePolicyContext context) {
    return context.amount == 0;
  }

  @Override
  public long getAllowedWithdraw(FreezeAccountCapsule fbo, UnfreezePolicyContext context) {
    return 0;
  }

  @Override
  public void withdraw(FreezeAccountCapsule fbo, AccountCapsule accountCapsule,
      UnfreezePolicyContext context, boolean isAccountModified, boolean isFreezeAccountModified) {
  }
}

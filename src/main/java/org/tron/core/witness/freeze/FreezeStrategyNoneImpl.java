package org.tron.core.witness.freeze;

import org.tron.core.capsule.AccountCapsule;

//No freeze in funds
public class FreezeStrategyNoneImpl implements FreezeStrategy {

  @Override
  public boolean isFreezeAllowed(FreezeBalanceObject fbo, FreezePolicyContext context) {
    return true;
  }

  @Override
  public void freeze(FreezeBalanceObject fbo, AccountCapsule accountCapsule,
      FreezePolicyContext context) {
    //Direct deposit of account balance
    accountCapsule.setBalance(accountCapsule.getBalance() + context.amount);
  }

  @Override
  public boolean isWithdrawAllowed(FreezeBalanceObject fbo, UnfreezePolicyContext context) {
    return context.amount == 0;
  }

  @Override
  public long getAllowedWithdraw(FreezeBalanceObject fbo, UnfreezePolicyContext context) {
    return 0;
  }

  @Override
  public void withdraw(FreezeBalanceObject fbo, AccountCapsule accountCapsule,
      UnfreezePolicyContext context) {
  }
}

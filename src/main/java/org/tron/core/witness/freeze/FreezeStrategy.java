package org.tron.core.witness.freeze;

import org.tron.core.capsule.AccountCapsule;

public interface FreezeStrategy {

  boolean isFreezeAllowed(FreezeBalanceObject object, FreezePolicyContext context);

  void freeze(FreezeBalanceObject object, AccountCapsule accountCapsule,
      FreezePolicyContext context);

  boolean isWithdrawAllowed(FreezeBalanceObject object, UnfreezePolicyContext context);

  long getAllowedWithdraw(FreezeBalanceObject object, UnfreezePolicyContext context);

  void withdraw(FreezeBalanceObject object, AccountCapsule accountCapsule,
      UnfreezePolicyContext context);


  public static FreezeStrategy createFreezeStrategy(StakeStrategyType strategyType) {
    switch (strategyType) {
      case None:
        return new FreezeStrategyNoneImpl();
      case Linear:
        return new FreezeStrategyLinearImpl();
      default:
        throw new RuntimeException("unknown strategyType[" + strategyType + "]");
    }

  }

  static public class FreezePolicyContext {

    long now;
    long amount;
  }

  static public class UnfreezePolicyContext {

    long now;
    long amount;
  }

  static public enum StakeStrategyType {
    None,
    Linear;
  }
}

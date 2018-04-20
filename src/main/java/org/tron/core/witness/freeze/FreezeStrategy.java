package org.tron.core.witness.freeze;


import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.FreezeAccountCapsule;
import org.tron.core.db.AccountStore;
import org.tron.core.db.FreezeAccountStore;

public interface FreezeStrategy {

  boolean isFreezeAllowed(FreezeAccountCapsule object, FreezePolicyContext context);

  void freeze(FreezeAccountCapsule object, AccountCapsule accountCapsule,
      FreezePolicyContext context, boolean isAccountModified, boolean isFreezeAccountModified);

  boolean isWithdrawAllowed(FreezeAccountCapsule object, UnfreezePolicyContext context);

  long getAllowedWithdraw(FreezeAccountCapsule object, UnfreezePolicyContext context);

  void withdraw(FreezeAccountCapsule object, AccountCapsule accountCapsule,
      UnfreezePolicyContext context, boolean isAccountModified, boolean isFreezeAccountModified);


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

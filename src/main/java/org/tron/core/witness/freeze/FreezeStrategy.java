package org.tron.core.witness.freeze;


import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.FreezeAccountCapsule;

public interface FreezeStrategy {

  boolean isFreezeAllowed(FreezeAccountCapsule object, FreezePolicyContext context);

  void freeze(FreezeAccountCapsule object, AccountCapsule accountCapsule,
      FreezePolicyContext context, AccountModifiedResult accountModifiedResult);

  boolean isWithdrawAllowed(FreezeAccountCapsule object, withdrawPolicyContext context);

  long getAllowedWithdraw(FreezeAccountCapsule object, withdrawPolicyContext context);

  void withdraw(FreezeAccountCapsule object, AccountCapsule accountCapsule,
      withdrawPolicyContext context, AccountModifiedResult accountModifiedResult);


  public static FreezeStrategy createFreezeStrategy(StakeStrategyType strategyType) {
    switch (strategyType) {
      case None:
        return new FreezeStrategyNone();
      case Linear:
        return new FreezeStrategyLinear();
      default:
        throw new RuntimeException("unknown strategyType[" + strategyType + "]");
    }

  }

  static public class FreezePolicyContext {

    public long now;
    public long amount;
  }

  static public class withdrawPolicyContext {

    public long now;
    public long amount;
  }

  static public class AccountModifiedResult {

    public boolean isAccountModified;
    public boolean isFreezeAccountModified;
  }

  static public enum StakeStrategyType {
    None,
    Linear;
  }
}

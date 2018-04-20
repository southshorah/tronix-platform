package org.tron.core.witness.freeze;


import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.FreezeAccountCapsule;

public interface FreezeStrategy {

  boolean isFreezeAllowed(FreezeAccountCapsule object, FreezePolicyContext context);

  void freeze(FreezeAccountCapsule object, AccountCapsule accountCapsule,
      FreezePolicyContext context, AccountModifiedResult accountModifiedResult);

  boolean isWithdrawAllowed(FreezeAccountCapsule object, WithdrawPolicyContext context);

  long getAllowedWithdraw(FreezeAccountCapsule object, WithdrawPolicyContext context);

  void withdraw(FreezeAccountCapsule object, AccountCapsule accountCapsule,
      WithdrawPolicyContext context, AccountModifiedResult accountModifiedResult);


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

  public static FreezePolicyContext createFreezePolicyContext(long now, long amount) {
    FreezePolicyContext context = new FreezePolicyContext();
    context.now = now;
    context.amount = amount;
    return context;
  }

  public static WithdrawPolicyContext createWithdrawPolicyContext(long now, long amount) {
    WithdrawPolicyContext context = new WithdrawPolicyContext();
    context.now = now;
    context.amount = amount;
    return context;
  }


  static public class FreezePolicyContext {

    public long now;
    public long amount;
  }

  static public class WithdrawPolicyContext {

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

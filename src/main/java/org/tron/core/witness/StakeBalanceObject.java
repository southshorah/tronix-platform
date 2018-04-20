package org.tron.core.witness;

import com.google.common.base.Preconditions;
import org.tron.core.capsule.AccountCapsule;

public class StakeBalanceObject {

  private AccountCapsule accountCapsule;
  private long balance;
  private StakeStrategyType strategyType;
  private FreezeStrategy freezeStrategy; //todo: factory

  public StakeBalanceObject(AccountCapsule accountCapsule, long balance,
      StakeStrategyType strategyType) {
    this.accountCapsule = accountCapsule;
    this.balance = balance;
    this.strategyType = StakeStrategyType.Linear;
  }

  static public class FreezePolicyContext {
    long balance;
    long now;
    long amount;
  }

  static public enum StakeStrategyType {
    None,
    Linear;
  }

  static public interface FreezeStrategy {

    long getAllowedUnfreeze(FreezePolicyContext context);

    boolean isFreezeAllowed(FreezePolicyContext context);

    boolean isUnfreezeAllowed(FreezePolicyContext context);

    void freeze(FreezePolicyContext context);

    void unfreeze(FreezePolicyContext context);


  }

  public class FreezeStrategyImpl implements FreezeStrategy {

    int freezeSeconds = 0;
    long coinSecondsEarned;
    long startClaim;
    long coinSecondsEarnedLastUpdate;

    private long computeCoinSecondsEarned(FreezePolicyContext context) {
      Preconditions.checkArgument(context.now >= coinSecondsEarnedLastUpdate);
      long deltaSeconds = (context.now - coinSecondsEarnedLastUpdate) / 1000;
      Preconditions.checkArgument(deltaSeconds >= 0);

      long deltaCoinSeconds = context.balance;
      deltaCoinSeconds *= deltaSeconds;

      long coinSecondsEarnedCap = context.amount;
      coinSecondsEarnedCap *= Math.max(freezeSeconds, 1);

      return Math.min(coinSecondsEarned + deltaCoinSeconds, coinSecondsEarnedCap);
    }

    private void updateCoinSecondsEarned(FreezePolicyContext context) {
      coinSecondsEarned = computeCoinSecondsEarned(context);
      coinSecondsEarnedLastUpdate = context.now;
    }

    @Override
    public long getAllowedUnfreeze(FreezePolicyContext context) {
      if (context.now <= startClaim) {
        return 0;
      }
      long csEarned = computeCoinSecondsEarned(context);
      long unfreezeAvailable = csEarned / Math.min(freezeSeconds, 1);
      Preconditions.checkArgument(unfreezeAvailable <= context.balance);
      return unfreezeAvailable;
    }

    @Override
    public boolean isFreezeAllowed(FreezePolicyContext context) {
      return true;
    }

    @Override
    public boolean isUnfreezeAllowed(FreezePolicyContext context) {
      return context.amount <= getAllowedUnfreeze(context);
    }

    @Override
    public void freeze(FreezePolicyContext context) {
      updateCoinSecondsEarned(context);
    }


    @Override
    public void unfreeze(FreezePolicyContext context) {
      updateCoinSecondsEarned(context);
      long coinSecondsNeeded = context.amount;
      coinSecondsNeeded *= Math.max(freezeSeconds, 1);
      Preconditions.checkArgument(coinSecondsNeeded <= coinSecondsEarned);

      coinSecondsEarned -= coinSecondsNeeded;
    }

  }

}

package org.tron.core.witness;

import com.google.common.base.Preconditions;
import org.tron.core.capsule.AccountCapsule;

public class StakeBalanceObject {

  private AccountCapsule accountCapsule;
  private long balance;
  private StakeStrategyType strategyType;

  public StakeBalanceObject(AccountCapsule accountCapsule, long balance,
      StakeStrategyType strategyType) {
    this.accountCapsule = accountCapsule;
    this.balance = balance;
    this.strategyType = StakeStrategyType.Linear;
  }

  static public class stakeStrategyMessage {

    long balance;
    long now;
    long amount;
  }

  static public enum StakeStrategyType {
    None,
    Linear;
  }

  static public interface FreezeStrategy {

    boolean freeze();

    long getAllowedUnfreezeBalance();

    boolean unfreeze();

  }

  static public class LinearFreezeStrategy {

    long beginTimestamp;//this is the time when freezing begin
    long freezeCliffSeconds;//before the time,balance cannot be froze
    long freezeDurationSeconds;//
    long beginBalance;

    public boolean freeze() {
      return false;
    }

    public long getAllowedUnfreezeBalance(stakeStrategyMessage strategyObject) {
      long allowedWithdraw = 0;
      if (strategyObject.now > beginTimestamp) {
        long elapsedTime = strategyObject.now - beginTimestamp;
        Preconditions.checkArgument(elapsedTime > 0, "");
        if (elapsedTime >= freezeCliffSeconds) {
          long totalFroze = 0;
          if (elapsedTime < freezeDurationSeconds) {
            totalFroze = beginBalance * elapsedTime / freezeDurationSeconds;
          } else {
            totalFroze = beginBalance;
          }
          Preconditions.checkArgument(totalFroze > 0, "");
          long unfreezeAlready = beginBalance - strategyObject.balance;
          Preconditions.checkArgument(unfreezeAlready > 0, "");
          allowedWithdraw = totalFroze - unfreezeAlready;
          Preconditions.checkArgument(allowedWithdraw > 0, "");
        }

      }
      return allowedWithdraw;
    }

    public boolean unfreeze() {
      return false;
    }

  }

}

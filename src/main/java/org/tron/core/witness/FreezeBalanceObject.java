package org.tron.core.witness;

import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.AccountCapsule;

@Slf4j
public class FreezeBalanceObject {

  private AccountCapsule accountCapsule;

  private long lastWithdrawTime;
  private long lastFreezeTime;
  private long freezeBalance;
  private long unfreezeBalance;

  private StakeStrategyType strategyType = StakeStrategyType.Linear;
  private FreezeStrategy freezeStrategy;

  public FreezeBalanceObject(AccountCapsule accountCapsule, long balance) {
    this.accountCapsule = accountCapsule;
    this.freezeStrategy = createFreezeStrategy(strategyType);
  }

  public boolean isFreezeAllowed(FreezePolicyContext context) {
    return freezeStrategy.isFreezeAllowed(context);
  }

  //todo: store
  public void freeze(FreezePolicyContext context) {
    freezeStrategy.freeze(context);
  }

  public boolean isWithdrawAllowed(UnfreezePolicyContext context) {
    return freezeStrategy.isWithdrawAllowed(context);
  }

  public long getAllowedWithdraw(UnfreezePolicyContext context) {
    return freezeStrategy.getAllowedWithdraw(context);
  }

  //todo: store
  public void withdraw(UnfreezePolicyContext context) {
    freezeStrategy.withdraw(context);
  }

  public FreezeStrategy createFreezeStrategy(StakeStrategyType strategyType) {
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

  static public interface FreezeStrategy {

    boolean isFreezeAllowed(FreezePolicyContext context);

    void freeze(FreezePolicyContext context);

    boolean isWithdrawAllowed(UnfreezePolicyContext context);

    long getAllowedWithdraw(UnfreezePolicyContext context);

    void withdraw(UnfreezePolicyContext context);


  }

  //No freeze in funds
  public class FreezeStrategyNoneImpl implements FreezeStrategy {

    @Override
    public boolean isFreezeAllowed(FreezePolicyContext context) {
      return true;
    }

    @Override
    public void freeze(FreezePolicyContext context) {
      //Direct deposit of account balance
      accountCapsule.setBalance(accountCapsule.getBalance() + context.amount);
    }

    @Override
    public boolean isWithdrawAllowed(UnfreezePolicyContext context) {
      return context.amount == 0;
    }

    @Override
    public long getAllowedWithdraw(UnfreezePolicyContext context) {
      return 0;
    }

    @Override
    public void withdraw(UnfreezePolicyContext context) {
    }
  }


  public class FreezeStrategyLinearImpl implements FreezeStrategy {

    // 24 * 60 * 60 * 1000 * WITNESS_PAY_PER_BLOCK / (Manager.LOOP_INTERVAL * 27);
    public final static long MAX_FREEZE_BALANCE = 20480000000L;//DROP
    public final static long MIN_WITHDRAW_PERIOD = 86400000L;//24 * 60 * 60 * 1000 ms
    public final static long MIN_ALL_WITHDRAW_PERIOD = 259200000L;//3 * 24 * 60 * 60 * 1000 ms
    public final static long MAX_BALANCE = Long.MAX_VALUE;


    @Override
    public boolean isFreezeAllowed(FreezePolicyContext context) {
      if (context.amount < 0) {
        logger.info("context.amount < 0");
        return false;
      }
      if (context.amount + unfreezeBalance > MAX_BALANCE) {
        logger.info("context.amount + unfreezeBalance > MAX_BALANCE");
        return false;
      }
      if (context.now < lastFreezeTime) {
        logger.info("context.now < lastFreezeTime");
        return false;
      }

      return true;
    }

    @Override
    public void freeze(FreezePolicyContext context) {
      //If the total amount exceeds the maximum limit, do not freeze
      if (freezeBalance + context.amount >= MAX_FREEZE_BALANCE) {
        unfreezeBalance += context.amount;
      } else {
        freezeBalance += context.amount;
      }

      lastFreezeTime = context.now;
    }

    @Override
    public boolean isWithdrawAllowed(UnfreezePolicyContext context) {

      //Limit the withdrawal frequency
      if (context.now - lastWithdrawTime < MIN_WITHDRAW_PERIOD) {
        logger.info("context.now[{}] - lastWithdrawTime[{}] < MIN_WITHDRAW_PERIOD", context.now,
            lastWithdrawTime);
        return false;
      }

      if (context.now - lastFreezeTime >= MIN_ALL_WITHDRAW_PERIOD) {
        //Allow to withdraw all balance if more than 3 days from the last freeze
        return context.amount <= freezeBalance + unfreezeBalance;
      } else {
        //Allow to withdraw unfreezeBalance only
        return context.amount <= unfreezeBalance;
      }

    }


    @Override
    public long getAllowedWithdraw(UnfreezePolicyContext context) {
      if (context.now - lastFreezeTime >= MIN_ALL_WITHDRAW_PERIOD) {
        //Allow to withdraw all balance if more than 3 days from the last freeze
        return freezeBalance + unfreezeBalance;
      } else {
        //Allow to withdraw unfreezeBalance only
        return unfreezeBalance;
      }
    }

    @Override
    public void withdraw(UnfreezePolicyContext context) {
      if (unfreezeBalance >= context.amount) {
        unfreezeBalance -= context.amount;
      } else {
        unfreezeBalance = 0;
        freezeBalance -= context.amount - unfreezeBalance;
      }

      accountCapsule.setBalance(accountCapsule.getBalance() + context.amount);

      lastWithdrawTime = context.now;
    }


  }

}

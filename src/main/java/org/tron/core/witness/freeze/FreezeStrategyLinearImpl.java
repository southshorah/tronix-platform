package org.tron.core.witness.freeze;


import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.FreezeAccountCapsule;
import org.tron.core.db.AccountStore;
import org.tron.core.db.FreezeAccountStore;

@Slf4j
public class FreezeStrategyLinearImpl implements FreezeStrategy {

  // 24 * 60 * 60 * 1000 * WITNESS_PAY_PER_BLOCK / (Manager.LOOP_INTERVAL * 27);
  public final static long MAX_FREEZE_BALANCE = 20480000000L;//DROP
  public final static long MIN_WITHDRAW_PERIOD = 86400000L;//24 * 60 * 60 * 1000 ms
  public final static long MIN_ALL_WITHDRAW_PERIOD = 259200000L;//3 * 24 * 60 * 60 * 1000 ms
  public final static long MAX_BALANCE = Long.MAX_VALUE;


  @Override
  public boolean isFreezeAllowed(FreezeAccountCapsule fbo, FreezePolicyContext context) {
    if (context.amount < 0) {
      logger.info("context.amount < 0");
      return false;
    }
    if (context.amount + fbo.getUnfreezeBalance() > MAX_BALANCE) {
      logger.info("context.amount + unfreezeBalance > MAX_BALANCE");
      return false;
    }
    if (context.now < fbo.getLastFreezeTime()) {
      logger.info("context.now < lastFreezeTime");
      return false;
    }

    return true;
  }

  @Override
  public void freeze(FreezeAccountCapsule fbo, AccountCapsule accountCapsule,
      FreezePolicyContext context, boolean isAccountModified, boolean isFreezeAccountModified) {
    //If the total amount exceeds the maximum limit, do not freeze
    if (fbo.getFreezeBalance() + context.amount >= MAX_FREEZE_BALANCE) {
      fbo.setUnfreezeBalance(fbo.getUnfreezeBalance() + context.amount);
    } else {
      fbo.setFreezeBalance(fbo.getFreezeBalance() + context.amount);
    }
    fbo.setLastFreezeTime(context.now);
  }

  @Override
  public boolean isWithdrawAllowed(FreezeAccountCapsule fbo, UnfreezePolicyContext context) {

    //Limit the withdrawal frequency
    if (context.now - fbo.getLastWithdrawTime() < MIN_WITHDRAW_PERIOD) {
      logger.info("context.now[{}] - lastWithdrawTime[{}] < MIN_WITHDRAW_PERIOD", context.now,
          fbo.getLastWithdrawTime());
      return false;
    }

    if (context.now - fbo.getLastFreezeTime() >= MIN_ALL_WITHDRAW_PERIOD) {
      //Allow to withdraw all balance if more than 3 days from the last freeze
      return context.amount <= fbo.getFreezeBalance() + fbo.getUnfreezeBalance();
    } else {
      //Allow to withdraw unfreezeBalance only
      return context.amount <= fbo.getUnfreezeBalance();
    }

  }


  @Override
  public long getAllowedWithdraw(FreezeAccountCapsule fbo, UnfreezePolicyContext context) {
    if (context.now - fbo.getLastFreezeTime() >= MIN_ALL_WITHDRAW_PERIOD) {
      //Allow to withdraw all balance if more than 3 days from the last freeze
      return fbo.getFreezeBalance() + fbo.getUnfreezeBalance();
    } else {
      //Allow to withdraw unfreezeBalance only
      return fbo.getUnfreezeBalance();
    }
  }

  @Override
  public void withdraw(FreezeAccountCapsule fbo, AccountCapsule accountCapsule,
      UnfreezePolicyContext context, boolean isAccountModified, boolean isFreezeAccountModified) {
    if (fbo.getUnfreezeBalance() >= context.amount) {
      fbo.setUnfreezeBalance(fbo.getUnfreezeBalance() - context.amount);
    } else {
      fbo.setUnfreezeBalance(0);
      fbo.setFreezeBalance(fbo.getFreezeBalance() + fbo.getUnfreezeBalance() - context.amount);
    }

    accountCapsule.setBalance(accountCapsule.getBalance() + context.amount);

    fbo.setLastWithdrawTime(context.now);

  }


}

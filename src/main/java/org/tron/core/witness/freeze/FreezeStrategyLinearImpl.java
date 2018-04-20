package org.tron.core.witness.freeze;


import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.AccountCapsule;

@Slf4j
public class FreezeStrategyLinearImpl implements FreezeStrategy {

  // 24 * 60 * 60 * 1000 * WITNESS_PAY_PER_BLOCK / (Manager.LOOP_INTERVAL * 27);
  public final static long MAX_FREEZE_BALANCE = 20480000000L;//DROP
  public final static long MIN_WITHDRAW_PERIOD = 86400000L;//24 * 60 * 60 * 1000 ms
  public final static long MIN_ALL_WITHDRAW_PERIOD = 259200000L;//3 * 24 * 60 * 60 * 1000 ms
  public final static long MAX_BALANCE = Long.MAX_VALUE;


  @Override
  public boolean isFreezeAllowed(FreezeBalanceObject fbo, FreezePolicyContext context) {
    if (context.amount < 0) {
      logger.info("context.amount < 0");
      return false;
    }
    if (context.amount + fbo.unfreezeBalance > MAX_BALANCE) {
      logger.info("context.amount + unfreezeBalance > MAX_BALANCE");
      return false;
    }
    if (context.now < fbo.lastFreezeTime) {
      logger.info("context.now < lastFreezeTime");
      return false;
    }

    return true;
  }

  @Override
  public void freeze(FreezeBalanceObject fbo, AccountCapsule accountCapsule,
      FreezePolicyContext context) {
    //If the total amount exceeds the maximum limit, do not freeze
    if (fbo.freezeBalance + context.amount >= MAX_FREEZE_BALANCE) {
      fbo.unfreezeBalance += context.amount;
    } else {
      fbo.freezeBalance += context.amount;
    }

    fbo.lastFreezeTime = context.now;
  }

  @Override
  public boolean isWithdrawAllowed(FreezeBalanceObject fbo, UnfreezePolicyContext context) {

    //Limit the withdrawal frequency
    if (context.now - fbo.lastWithdrawTime < MIN_WITHDRAW_PERIOD) {
      logger.info("context.now[{}] - lastWithdrawTime[{}] < MIN_WITHDRAW_PERIOD", context.now,
          fbo.lastWithdrawTime);
      return false;
    }

    if (context.now - fbo.lastFreezeTime >= MIN_ALL_WITHDRAW_PERIOD) {
      //Allow to withdraw all balance if more than 3 days from the last freeze
      return context.amount <= fbo.freezeBalance + fbo.unfreezeBalance;
    } else {
      //Allow to withdraw unfreezeBalance only
      return context.amount <= fbo.unfreezeBalance;
    }

  }


  @Override
  public long getAllowedWithdraw(FreezeBalanceObject fbo, UnfreezePolicyContext context) {
    if (context.now - fbo.lastFreezeTime >= MIN_ALL_WITHDRAW_PERIOD) {
      //Allow to withdraw all balance if more than 3 days from the last freeze
      return fbo.freezeBalance + fbo.unfreezeBalance;
    } else {
      //Allow to withdraw unfreezeBalance only
      return fbo.unfreezeBalance;
    }
  }

  @Override
  public void withdraw(FreezeBalanceObject fbo, AccountCapsule accountCapsule,
      UnfreezePolicyContext context) {
    if (fbo.unfreezeBalance >= context.amount) {
      fbo.unfreezeBalance -= context.amount;
    } else {
      fbo.unfreezeBalance = 0;
      fbo.freezeBalance -= context.amount - fbo.unfreezeBalance;
    }

    accountCapsule.setBalance(accountCapsule.getBalance() + context.amount);

    fbo.lastWithdrawTime = context.now;
  }


}

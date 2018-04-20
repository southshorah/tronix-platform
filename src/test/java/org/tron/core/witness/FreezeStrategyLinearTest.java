package org.tron.core.witness;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.FreezeAccountCapsule;
import org.tron.core.witness.freeze.FreezeStrategy;
import org.tron.core.witness.freeze.FreezeStrategy.AccountModifiedResult;
import org.tron.core.witness.freeze.FreezeStrategy.FreezePolicyContext;
import org.tron.core.witness.freeze.FreezeStrategy.StakeStrategyType;
import org.tron.core.witness.freeze.FreezeStrategy.withdrawPolicyContext;
import org.tron.core.witness.freeze.FreezeStrategyLinear;
import org.tron.protos.Protocol.AccountType;

public class FreezeStrategyLinearTest {

  private static ByteString address = ByteString.copyFrom("00000000002".getBytes());

  private static FreezeStrategy strategy = FreezeStrategy
      .createFreezeStrategy(StakeStrategyType.Linear);

  @BeforeClass
  public static void init() {
  }

  @Test
  public void testIsFreezeAllowed() {
    FreezeAccountCapsule freezeAccountCapsule = new FreezeAccountCapsule(address);
    freezeAccountCapsule.setLastFreezeTime(
        DateTime.parse("20180201", DateTimeFormat.forPattern("yyyyMMdd")).getMillis());

    FreezePolicyContext freezePolicyContext = new FreezePolicyContext();
    freezePolicyContext.now = DateTime.parse("20180202", DateTimeFormat.forPattern("yyyyMMdd"))
        .getMillis();
    freezePolicyContext.amount = 100;

    boolean isFreezeAllowed;

    //Test the first mission

    isFreezeAllowed = strategy.isFreezeAllowed(freezeAccountCapsule, freezePolicyContext);
    assertEquals(true, isFreezeAllowed);

    //Test the amount < 0
    freezePolicyContext.amount = -100;
    isFreezeAllowed = strategy.isFreezeAllowed(freezeAccountCapsule, freezePolicyContext);
    assertEquals(false, isFreezeAllowed);

    //Test the amount is too large
    freezeAccountCapsule.setUnfreezeBalance(FreezeStrategyLinear.MAX_BALANCE);
    freezePolicyContext.amount = 100;
    isFreezeAllowed = strategy.isFreezeAllowed(freezeAccountCapsule, freezePolicyContext);
    assertEquals(false, isFreezeAllowed);

    //Test the freeze time is before lastFreezeTime
    freezeAccountCapsule.setUnfreezeBalance(0);
    freezePolicyContext.now = DateTime.parse("20180130", DateTimeFormat.forPattern("yyyyMMdd"))
        .getMillis();

    isFreezeAllowed = strategy.isFreezeAllowed(freezeAccountCapsule, freezePolicyContext);
    assertEquals(false, isFreezeAllowed);
  }

  @Test
  public void testFreeze() {
    FreezeAccountCapsule freezeAccountCapsule = new FreezeAccountCapsule(address);
    freezeAccountCapsule.setLastFreezeTime(
        DateTime.parse("20180201", DateTimeFormat.forPattern("yyyyMMdd")).getMillis());

    FreezePolicyContext freezePolicyContext = new FreezePolicyContext();
    freezePolicyContext.now = DateTime.parse("20180202", DateTimeFormat.forPattern("yyyyMMdd"))
        .getMillis();
    freezePolicyContext.amount = 100;

    AccountModifiedResult accountModifiedResult = new AccountModifiedResult();

    //test the total amount less than the maximum limit
    strategy.freeze(freezeAccountCapsule, null,
        freezePolicyContext, accountModifiedResult);

    assertEquals(freezePolicyContext.amount, freezeAccountCapsule.getFreezeBalance());
    assertEquals(0, freezeAccountCapsule.getUnfreezeBalance());
    assertEquals(0, freezeAccountCapsule.getUnfreezeBalance());
    assertEquals(freezePolicyContext.now, freezeAccountCapsule.getLastFreezeTime());
    assertEquals(false, accountModifiedResult.isAccountModified);
    assertEquals(true, accountModifiedResult.isFreezeAccountModified);

    //test the total amount exceeds the maximum limit
    freezeAccountCapsule.setFreezeBalance(FreezeStrategyLinear.MAX_FREEZE_BALANCE);
    strategy.freeze(freezeAccountCapsule, null,
        freezePolicyContext, accountModifiedResult);

    assertEquals(FreezeStrategyLinear.MAX_FREEZE_BALANCE, freezeAccountCapsule.getFreezeBalance());
    assertEquals(freezePolicyContext.amount, freezeAccountCapsule.getUnfreezeBalance());

  }

  @Test
  public void testIsWithdrawAllowed() {
    FreezeAccountCapsule freezeAccountCapsule = new FreezeAccountCapsule(address);
    withdrawPolicyContext withdrawPolicyContext = new withdrawPolicyContext();
    boolean isWithdrawAllowed;

    //Withdraw frequency is too fast
    freezeAccountCapsule.setLastWithdrawTime(
        DateTime.parse("20180201 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
            .getMillis());

    withdrawPolicyContext.now = DateTime
        .parse("20180201 11:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis();
    isWithdrawAllowed = strategy.isWithdrawAllowed(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(false, isWithdrawAllowed);

    //less than 3 days from the last freeze
    withdrawPolicyContext.now = DateTime
        .parse("20180202 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis();
    freezeAccountCapsule.setLastFreezeTime(DateTime
        .parse("20180202 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis());
    freezeAccountCapsule.setUnfreezeBalance(1000L);
    withdrawPolicyContext.amount = 1500L;
    isWithdrawAllowed = strategy.isWithdrawAllowed(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(false, isWithdrawAllowed);

    //more than 3 days from the last freeze
    freezeAccountCapsule.setLastFreezeTime(DateTime
        .parse("20180202 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis());
    withdrawPolicyContext.now = DateTime
        .parse("20180205 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis();
    freezeAccountCapsule.setFreezeBalance(1000L);
    freezeAccountCapsule.setUnfreezeBalance(1000L);

    isWithdrawAllowed = strategy.isWithdrawAllowed(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(true, isWithdrawAllowed);

  }

  @Test
  public void testGetAllowedWithdraw() {
    FreezeAccountCapsule freezeAccountCapsule = new FreezeAccountCapsule(address);
    freezeAccountCapsule.setFreezeBalance(1000L);
    freezeAccountCapsule.setUnfreezeBalance(1000L);
    long allowedWithdraw;

    withdrawPolicyContext withdrawPolicyContext = new withdrawPolicyContext();
    withdrawPolicyContext.amount = 1500L;

    // allow to withdraw all balance if more than 3 days from the last freeze
    freezeAccountCapsule.setLastFreezeTime(DateTime
        .parse("20180202 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis());
    withdrawPolicyContext.now = DateTime
        .parse("20180205 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis();
    allowedWithdraw = strategy.getAllowedWithdraw(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(
        freezeAccountCapsule.getUnfreezeBalance() + freezeAccountCapsule.getFreezeBalance(),
        allowedWithdraw);

    // withdraw unfreezeBalance only
    withdrawPolicyContext.now = DateTime
        .parse("20180203 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis();
    allowedWithdraw = strategy.getAllowedWithdraw(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(freezeAccountCapsule.getUnfreezeBalance(), allowedWithdraw);

  }

  @Test
  public void testWithdraw() {
    FreezeAccountCapsule freezeAccountCapsule = new FreezeAccountCapsule(address);

    withdrawPolicyContext withdrawPolicyContext = new withdrawPolicyContext();
    withdrawPolicyContext.now = DateTime
        .parse("20180203 10:00:00", DateTimeFormat.forPattern("yyyyMMdd HH:mm:ss"))
        .getMillis();

    AccountCapsule accountCapsule = new AccountCapsule(address,
        ByteString.copyFromUtf8("Marcus"),
        AccountType.Normal);

    AccountModifiedResult accountModifiedResult = new AccountModifiedResult();

    // withdraw unfreezeBalance only
    freezeAccountCapsule.setFreezeBalance(1000L);
    freezeAccountCapsule.setUnfreezeBalance(1000L);
    withdrawPolicyContext.amount = 600L;
    accountCapsule.setBalance(10000L);

    strategy.withdraw(freezeAccountCapsule, accountCapsule, withdrawPolicyContext,
        accountModifiedResult);
    assertEquals(1000L, freezeAccountCapsule.getFreezeBalance());
    assertEquals(400L, freezeAccountCapsule.getUnfreezeBalance());
    assertEquals(withdrawPolicyContext.now, freezeAccountCapsule.getLastWithdrawTime());

    assertEquals(10600L, accountCapsule.getBalance());

    assertEquals(true, accountModifiedResult.isAccountModified);
    assertEquals(true, accountModifiedResult.isFreezeAccountModified);

    // withdraw freezeBalance
    freezeAccountCapsule.setFreezeBalance(1000L);
    freezeAccountCapsule.setUnfreezeBalance(1000L);
    withdrawPolicyContext.amount = 1600L;
    accountCapsule.setBalance(10000L);

    strategy.withdraw(freezeAccountCapsule, accountCapsule, withdrawPolicyContext,
        accountModifiedResult);

    assertEquals(400L, freezeAccountCapsule.getFreezeBalance());
    assertEquals(0L, freezeAccountCapsule.getUnfreezeBalance());
    assertEquals(withdrawPolicyContext.now, freezeAccountCapsule.getLastWithdrawTime());

    assertEquals(11600L, accountCapsule.getBalance());

  }
}

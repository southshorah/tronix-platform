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
import org.tron.core.witness.freeze.FreezeStrategy.WithdrawPolicyContext;
import org.tron.protos.Protocol.AccountType;

public class FreezeStrategyNoneTest {

  private static ByteString address = ByteString.copyFrom("00000000002".getBytes());

  private static FreezeAccountCapsule freezeAccountCapsule = new FreezeAccountCapsule(address);
  private static AccountCapsule accountCapsule = new AccountCapsule(address,
      ByteString.copyFromUtf8("Marcus"),
      AccountType.Normal);
  private static FreezeStrategy strategy = FreezeStrategy
      .createFreezeStrategy(StakeStrategyType.None);
  private static FreezePolicyContext freezePolicyContext = new FreezePolicyContext();
  private static WithdrawPolicyContext withdrawPolicyContext = new WithdrawPolicyContext();

  @BeforeClass
  public static void init() {
    freezePolicyContext.now = DateTime.parse("20180101", DateTimeFormat.forPattern("yyyyMMdd"))
        .getMillis();
    freezePolicyContext.amount = 100;

    withdrawPolicyContext.now = DateTime.parse("20180101", DateTimeFormat.forPattern("yyyyMMdd"))
        .getMillis();
    withdrawPolicyContext.amount = 100;

  }

  @Test
  public void testIsFreezeAllowed() {
    boolean isFreezeAllowed = strategy.isFreezeAllowed(freezeAccountCapsule, freezePolicyContext);
    assertEquals(true, isFreezeAllowed);
  }

  @Test
  public void testFreeze() {

    AccountModifiedResult accountModifiedResult = new AccountModifiedResult();

    strategy.freeze(freezeAccountCapsule, accountCapsule,
        freezePolicyContext, accountModifiedResult);

    assertEquals(freezePolicyContext.amount, accountCapsule.getBalance());

    assertEquals(true, accountModifiedResult.isAccountModified);
    assertEquals(false, accountModifiedResult.isFreezeAccountModified);
  }

  @Test
  public void testIsWithdrawAllowed() {

    boolean isWithdrawAllowed = strategy
        .isWithdrawAllowed(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(false, isWithdrawAllowed);
  }

  @Test
  public void testGetAllowedWithdraw() {

    long allowedWithdraw = strategy.getAllowedWithdraw(freezeAccountCapsule, withdrawPolicyContext);
    assertEquals(0, allowedWithdraw);

  }

  @Test
  public void testWithdraw() {

    AccountModifiedResult accountModifiedResult = new AccountModifiedResult();

    strategy.withdraw(freezeAccountCapsule, accountCapsule,
        withdrawPolicyContext, accountModifiedResult);
    assertEquals(0, accountCapsule.getBalance());
  }
}

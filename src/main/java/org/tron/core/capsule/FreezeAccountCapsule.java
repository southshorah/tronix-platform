package org.tron.core.capsule;

import static org.tron.core.witness.freeze.FreezeStrategy.createFreezeStrategy;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ByteArray;
import org.tron.core.db.AccountStore;
import org.tron.core.db.FreezeAccountStore;
import org.tron.core.witness.freeze.FreezeStrategy;
import org.tron.core.witness.freeze.FreezeStrategy.AccountModifiedResult;
import org.tron.core.witness.freeze.FreezeStrategy.FreezePolicyContext;
import org.tron.core.witness.freeze.FreezeStrategy.StakeStrategyType;
import org.tron.core.witness.freeze.FreezeStrategy.WithdrawPolicyContext;
import org.tron.protos.Protocol.FreezeAccount;

@Slf4j
public class FreezeAccountCapsule implements ProtoCapsule<FreezeAccount> {

  FreezeAccount freezeAccount;

  private static final FreezeStrategy freezeStrategy;

  static {
    freezeStrategy = createFreezeStrategy(StakeStrategyType.Linear);
  }


  public FreezeAccountCapsule(final ByteString address) {
    final FreezeAccount.Builder builder = FreezeAccount.newBuilder();
    this.freezeAccount = builder.setAccountAddress(address).build();
  }

  public FreezeAccountCapsule(final FreezeAccount freezeAccount) {
    this.freezeAccount = freezeAccount;
  }

  public FreezeAccountCapsule(final byte[] data) {
    try {
      this.freezeAccount = FreezeAccount.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public ByteString getAddress() {
    return this.freezeAccount.getAccountAddress();
  }

  public byte[] createDbKey() {
    return getAddress().toByteArray();
  }

  public String createReadableString() {
    return ByteArray.toHexString(getAddress().toByteArray());
  }

  @Override
  public byte[] getData() {
    return this.freezeAccount.toByteArray();
  }

  @Override
  public FreezeAccount getInstance() {
    return this.freezeAccount;
  }


  public boolean isFreezeAllowed(FreezePolicyContext context) {
    return freezeStrategy.isFreezeAllowed(this, context);
  }

  public void freeze(AccountCapsule accountCapsule,
      FreezePolicyContext context, AccountStore accountStore,
      FreezeAccountStore freezeAccountStore) {

    AccountModifiedResult accountModifiedResult = new AccountModifiedResult();

    freezeStrategy.freeze(this, accountCapsule, context, accountModifiedResult);

    if (accountModifiedResult.isAccountModified) {
      accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    }
    if (accountModifiedResult.isFreezeAccountModified) {
      freezeAccountStore.put(this.createDbKey(), this);
    }
  }

  public boolean isWithdrawAllowed(WithdrawPolicyContext context) {
    return freezeStrategy.isWithdrawAllowed(this, context);
  }

  public long getAllowedWithdraw(WithdrawPolicyContext context) {
    return freezeStrategy.getAllowedWithdraw(this, context);
  }

  public void withdraw(AccountCapsule accountCapsule, WithdrawPolicyContext context,
      AccountStore accountStore, FreezeAccountStore freezeAccountStore) {

    AccountModifiedResult accountModifiedResult = new AccountModifiedResult();

    freezeStrategy.withdraw(this, accountCapsule, context, accountModifiedResult);

    if (accountModifiedResult.isAccountModified) {
      accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    }
    if (accountModifiedResult.isFreezeAccountModified) {
      freezeAccountStore.put(this.createDbKey(), this);
    }
  }


  public long getLastWithdrawTime() {
    return freezeAccount.getLastWithdrawTime();
  }

  public long getLastFreezeTime() {
    return freezeAccount.getLastFreezeTime();
  }

  public long getFreezeBalance() {
    return freezeAccount.getFreezeBalance();
  }

  public long getUnfreezeBalance() {
    return freezeAccount.getUnfreezeBalance();
  }

  public void setLastWithdrawTime(long lastWithdrawTime) {
    freezeAccount = freezeAccount.toBuilder().setLastWithdrawTime(lastWithdrawTime).build();
  }

  public void setLastFreezeTime(long lastFreezeTime) {
    freezeAccount = freezeAccount.toBuilder().setLastFreezeTime(lastFreezeTime).build();
  }

  public void setFreezeBalance(long freezeBalance) {
    freezeAccount = freezeAccount.toBuilder().setFreezeBalance(freezeBalance).build();
  }

  public void setUnfreezeBalance(long unfreezeBalance) {
    freezeAccount = freezeAccount.toBuilder().setUnfreezeBalance(unfreezeBalance).build();
  }


}

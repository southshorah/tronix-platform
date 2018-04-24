package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.Wallet;
import org.tron.core.capsule.FreezeAccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.WithdrawWitnessContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class WithdrawWitnessPayActuator extends AbstractActuator {

  WithdrawWitnessPayActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final WithdrawWitnessContract contract = this.contract
          .unpack(WithdrawWitnessContract.class);
      ret.setStatus(fee, code.SUCESS);
      byte[] key = contract.getAccountAddress().toByteArray();
      dbManager.withdrawWitnessPay(key,contract.getTime(),contract.getAmount());
    } catch (final InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      if (!this.contract.is(WithdrawWitnessContract.class)) {
        throw new ContractValidateException(
            "contract type error,expected type [WithdrawWitnessContract],real type[" + this.contract
                .getClass() + "]");
      }

      final WithdrawWitnessContract contract = this.contract.unpack(WithdrawWitnessContract.class);
      byte[] key = contract.getAccountAddress().toByteArray();

      if (!Wallet.addressValid(contract.getAccountAddress().toByteArray())) {
        throw new ContractValidateException("Invalidate address");
      }
      if (this.dbManager.getWitnessStore().get(contract.getAccountAddress().toByteArray())
          == null) {
        throw new ContractValidateException("Witness not existed");
      }

      if (this.dbManager.getFreezeAccountStore().get(contract.getAccountAddress().toByteArray())
          == null) {
        throw new ContractValidateException("FreezeAccount not existed");
      }
      boolean allowedWithdraw = dbManager.isWithdrawAllowed(key,contract.getTime(),contract.getAmount());
      if(!allowedWithdraw){
        throw new ContractValidateException("not allowed to Withdraw");
      }

    } catch (final Exception ex) {
      ex.printStackTrace();
      throw new ContractValidateException(ex.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(WithdrawWitnessContract.class).getAccountAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}

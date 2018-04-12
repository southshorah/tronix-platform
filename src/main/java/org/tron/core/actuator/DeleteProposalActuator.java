package org.tron.core.actuator;

import com.google.common.base.Preconditions;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.ProposalCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.CreateProposalContract;
import org.tron.protos.Contract.DeleteProposalContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class DeleteProposalActuator extends AbstractActuator {

  public DeleteProposalActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final DeleteProposalContract contract = this.contract
          .unpack(DeleteProposalContract.class);
      deleteProposal(contract);
      ret.setStatus(fee, code.SUCESS);
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
      if (!this.contract.is(DeleteProposalContract.class)) {
        throw new ContractValidateException(
            "contract type error,expected type [DeleteProposalContract],real type[" + this.contract
                .getClass() + "]");
      }

      final DeleteProposalContract contract = this.contract.unpack(DeleteProposalContract.class);

      if (!Wallet.addressValid(contract.getOwnerAddress().toByteArray())) {
        throw new ContractValidateException("Invalidate address");
      }
      //todo , check committee account,and verify signature
      Preconditions.checkArgument(
          this.dbManager.getAccountStore().has(contract.getOwnerAddress().toByteArray()),
          "committee not exists");

      Preconditions.checkArgument(
          !this.dbManager.getProposalStore().has(contract.getProposalId().toByteArray()),
          "Proposal not existed");

      ProposalCapsule proposalCapsule = this.dbManager.getProposalStore()
          .get(contract.getProposalId().toByteArray());

      Preconditions
          .checkArgument(contract.getOwnerAddress().equals(proposalCapsule.getProposerAddress()),
              "account[" + StringUtil.createReadableString(contract.getOwnerAddress())
                  + "]  has no right to delete proposal:[" + proposalCapsule
                  .getProposerAddress() + "]");


    } catch (final Exception ex) {
      ex.printStackTrace();
      throw new ContractValidateException(ex.getMessage());
    }
    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(CreateProposalContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }


  private void deleteProposal(DeleteProposalContract contract) {
    this.dbManager.getProposalStore().delete(contract.getProposalId().toByteArray());
  }

}

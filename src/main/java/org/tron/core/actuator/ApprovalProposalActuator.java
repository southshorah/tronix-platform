package org.tron.core.actuator;

import com.google.common.base.Preconditions;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.ProposalCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.ApprovalProposalContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class ApprovalProposalActuator extends AbstractActuator {

  public ApprovalProposalActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final ApprovalProposalContract contract = this.contract
          .unpack(ApprovalProposalContract.class);
      ProposalCapsule proposalCapsule = updateProposal(contract);

      if (proposalCapsule.meetTheConditions()) {
        applyProposal(proposalCapsule);
      }

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
      if (!this.contract.is(ApprovalProposalContract.class)) {
        throw new ContractValidateException(
            "contract type error,expected type [ApprovalProposalContract],real type["
                + this.contract
                .getClass() + "]");
      }

      final ApprovalProposalContract contract = this.contract
          .unpack(ApprovalProposalContract.class);

      //todo : verify signature

      ProposalCapsule proposalCapsule = this.dbManager.getProposalStore()
          .get(contract.getProposalId().toByteArray());
      List<ByteString> approvalsList = proposalCapsule.getInstance().getApprovalsList();

      contract.getApprovalsToAddList().forEach(approval -> {
        Preconditions.checkArgument(approvalsList.contains(approval),
            "approvalToAdd[" + approval + "]  has existed");
      });

      contract.getApprovalsToRemoveList().forEach(approval -> {
        Preconditions.checkArgument(!approvalsList.contains(approval),
            "approvalToRemove[" + approval + "] not existed");
      });


    } catch (final Exception ex) {
      ex.printStackTrace();
      throw new ContractValidateException(ex.getMessage());
    }
    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ApprovalProposalContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }


  private ProposalCapsule updateProposal(ApprovalProposalContract contract) {
    ProposalCapsule proposalCapsule = this.dbManager.getProposalStore()
        .get(contract.getProposalId().toByteArray());

    proposalCapsule.addApprovals(contract.getApprovalsToAddList());
    proposalCapsule.removeApprovals(contract.getApprovalsToRemoveList());
    this.dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
    logger.debug("updateProposal,address[{}]", proposalCapsule.createReadableString());
    return proposalCapsule;
  }

  private void applyProposal(ProposalCapsule proposalCapsule) {
    this.dbManager.TRXS_SIZE = proposalCapsule.getInstance().getParameters().getTrxsSize();
  }

}

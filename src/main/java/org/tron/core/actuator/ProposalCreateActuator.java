package org.tron.core.actuator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.Wallet;
import org.tron.core.capsule.ProposalCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.CreateProposalContract;
import org.tron.protos.Protocol.ChainParameters;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j
public class ProposalCreateActuator extends AbstractActuator {

  public ProposalCreateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final CreateProposalContract contract = this.contract
          .unpack(CreateProposalContract.class);
      storeProposal(createProposal(contract));
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
      if (!this.contract.is(CreateProposalContract.class)) {
        throw new ContractValidateException(
            "contract type error,expected type [CreateProposalContract],real type[" + this.contract
                .getClass() + "]");
      }

      final CreateProposalContract contract = this.contract.unpack(CreateProposalContract.class);

      if (!Wallet.addressValid(contract.getOwnerAddress().toByteArray())) {
        throw new ContractValidateException("Invalidate address");
      }
      //todo , check committee account,and verify signature
      Preconditions.checkArgument(
          this.dbManager.getAccountStore().has(contract.getOwnerAddress().toByteArray()),
          "committee not exists");

      Preconditions.checkArgument(contract.getExpirationTime() > 0,
          "ExpirationTime must be positive");

      ChainParameters chainParameters = contract.getParameters();
      Preconditions.checkArgument(chainParameters.getTrxsSize() > 0,
          "TrxsSize must be positive");

      Preconditions.checkArgument(
          !this.dbManager.getProposalStore().has(createProposal(contract).createDbKey()),
          "Proposal has existed");

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

  private ProposalCapsule createProposal(final CreateProposalContract contract) {
    //Create Proposal  by CreateProposalContract
    final ProposalCapsule proposalCapsule = new ProposalCapsule(contract.getOwnerAddress(),
        contract.getParameters(), contract.getExpirationTime(), Lists.newArrayList());

    logger.debug("createProposal,address[{}]", proposalCapsule.createReadableString());
    return proposalCapsule;
  }

  private void storeProposal(ProposalCapsule proposalCapsule) {
    this.dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
  }

}

package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.Charset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Protocol.ChainParameters;
import org.tron.protos.Protocol.Proposal;

@Slf4j
public class ProposalCapsule implements ProtoCapsule<Proposal> {

  private Proposal proposal;

  public ProposalCapsule(final Proposal proposal) {
    if (proposal.getProposalId() == null) {
      this.proposal = proposal.toBuilder().setProposalId(createProposalId(proposal)).build();
    } else {
      this.proposal = proposal;
    }

  }

  public ProposalCapsule(ByteString proposerAddress, ChainParameters chainParameters,
      long expirationTime, List<ByteString> requiredApprovals) {
    Proposal newProposal = Proposal.newBuilder().setProposerAddress(proposerAddress)
        .setParameters(chainParameters)
        .setExpirationTime(expirationTime)
        .setEffectivePeriodTime(expirationTime)
        .addAllRequiredApprovals(requiredApprovals)
        .build();
    this.proposal = newProposal.toBuilder().setProposalId(createProposalId(newProposal)).build();
  }

  public ProposalCapsule(final byte[] data) {
    try {
      this.proposal = Proposal.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public byte[] createDbKey() {
    return getProposalId().toByteArray();
  }

  public String createReadableString() {
    return ByteArray.toHexString(getProposalId().toByteArray());
  }

  @Override
  public byte[] getData() {
    return this.proposal.toByteArray();
  }

  @Override
  public Proposal getInstance() {
    return this.proposal;
  }

  public ByteString getProposalId() {
    return this.proposal.getProposalId();
  }

  public ByteString getProposerAddress() {
    return this.proposal.getProposerAddress();
  }

  public static ByteString createProposalId(final Proposal proposal) {
    return ByteString.copyFrom(String.valueOf(proposal.hashCode()), Charset.defaultCharset());
  }

  public void addApprovals(List<ByteString> list) {
    this.proposal = this.proposal.toBuilder().addAllActiveApprovals(list).build();
  }

  public void removeApprovals(List<ByteString> removeApprovals) {
    final List<ByteString> approvalsList = this.proposal.toBuilder().getActiveApprovalsList();
    removeApprovals.forEach(approval -> {
      if (!approvalsList.contains(approval)) {
        logger.warn("proposal does not contain approval:{}", approval);
      }
      approvalsList.remove(approval);
    });
    this.proposal = this.proposal.toBuilder().clearActiveApprovals()
        .addAllActiveApprovals(approvalsList)
        .build();
  }

  public boolean hasExpired(long headBlockTime) {
    return getInstance().getExpirationTime() <= headBlockTime;
  }

  public boolean meetTheConditions() {
    //todo: verify conditions, and
    return this.proposal.getActiveApprovalsCount()
        > this.proposal.getRequiredApprovalsCount() * 2 / 3;
  }
}

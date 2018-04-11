package org.tron.core.db.api.index;

import static com.googlecode.cqengine.query.QueryFactory.attribute;

import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.navigable.NavigableIndex;
import com.googlecode.cqengine.index.suffix.SuffixTreeIndex;
import com.googlecode.cqengine.persistence.Persistence;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.protos.Contract.AccountCreateContract;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Contract.DeployContract;
import org.tron.protos.Contract.ParticipateAssetIssueContract;
import org.tron.protos.Contract.TransferAssetContract;
import org.tron.protos.Contract.TransferContract;
import org.tron.protos.Contract.VoteAssetContract;
import org.tron.protos.Contract.VoteWitnessContract;
import org.tron.protos.Contract.WitnessCreateContract;
import org.tron.protos.Contract.WitnessUpdateContract;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;

@Component
@Slf4j
public class BlockIndex extends AbstractIndex<Block> {

  public static final Attribute<Block, String> BLOCK_ID =
      attribute("block id",
          block -> Sha256Hash.of(block.getBlockHeader().toByteArray()).toString());
  public static final Attribute<Block, Long> BLOCK_NUMBER =
      attribute("block number",
          block -> block.getBlockHeader().getRawData().getNumber());
  public static final Attribute<Block, String> TRANSACTIONS =
      attribute(String.class, "transactions",
          block -> block.getTransactionsList().stream()
              .map(t -> Sha256Hash.of(t.toByteArray()).toString())
              .collect(Collectors.toList()));
  public static final Attribute<Block, String> RELATED_ACCOUNT =
      attribute(String.class, "transactions",
          block -> {
            List<String> list = Lists.newArrayList();
            for (Transaction transaction : block.getTransactionsList()) {
              List<String> relatedAccount = getRelatedAccount(transaction);
              list.addAll(relatedAccount);
            }
            return list;
          });
  public static final Attribute<Block, Long> WITNESS_ID =
      attribute("witness id",
          block -> block.getBlockHeader().getRawData().getWitnessId());
  public static final Attribute<Block, String> WITNESS_ADDRESS =
      attribute("witness address",
          block -> block.getBlockHeader().getRawData().getWitnessAddress().toStringUtf8());

  public BlockIndex() {
    super();
  }

  public BlockIndex(Persistence<Block, ? extends Comparable> persistence) {
    super(persistence);
  }

  private static List<String> getRelatedAccount(Transaction t) {
    List<String> addressList = Lists.newArrayList();
    t.getRawData().getContractList().forEach(contract -> {
      try {
        switch (contract.getType()) {
          case AccountCreateContract:
            AccountCreateContract accountCreateContract = contract.getParameter()
                .unpack(AccountCreateContract.class);
            addressList.add(
                ByteArray.toHexString(accountCreateContract.getOwnerAddress().toByteArray()));
            break;
          case TransferContract:
            TransferContract transferContract = contract.getParameter()
                .unpack(TransferContract.class);
            addressList.add(
                ByteArray.toHexString(transferContract.getOwnerAddress().toByteArray()));
            addressList.add(
                ByteArray.toHexString(transferContract.getToAddress().toByteArray()));
            break;
          case TransferAssetContract:
            TransferAssetContract transferAssetContract = contract.getParameter()
                .unpack(TransferAssetContract.class);
            addressList.add(
                ByteArray.toHexString(transferAssetContract.getOwnerAddress().toByteArray()));
            addressList.add(
                ByteArray.toHexString(transferAssetContract.getToAddress().toByteArray()));
            break;
          case VoteAssetContract:
            VoteAssetContract voteAssetContract = contract.getParameter()
                .unpack(VoteAssetContract.class);
            addressList.add(
                ByteArray.toHexString(voteAssetContract.getOwnerAddress().toByteArray()));
            break;
          case VoteWitnessContract:
            VoteWitnessContract voteWitnessContract = contract.getParameter()
                .unpack(VoteWitnessContract.class);
            addressList.add(
                ByteArray.toHexString(voteWitnessContract.getOwnerAddress().toByteArray()));
            break;
          case WitnessCreateContract:
            WitnessCreateContract witnessCreateContract = contract.getParameter()
                .unpack(WitnessCreateContract.class);
            addressList.add(
                ByteArray.toHexString(witnessCreateContract.getOwnerAddress().toByteArray()));
            break;
          case AssetIssueContract:
            AssetIssueContract assetIssueContract = contract.getParameter()
                .unpack(AssetIssueContract.class);
            addressList.add(
                ByteArray.toHexString(assetIssueContract.getOwnerAddress().toByteArray()));
            break;
          case DeployContract:
            DeployContract deployContract = contract.getParameter()
                .unpack(DeployContract.class);
            addressList.add(
                ByteArray.toHexString(deployContract.getOwnerAddress().toByteArray()));
            break;
          case WitnessUpdateContract:
            WitnessUpdateContract witnessUpdateContract = contract.getParameter()
                .unpack(WitnessUpdateContract.class);
            addressList.add(
                ByteArray.toHexString(witnessUpdateContract.getOwnerAddress().toByteArray()));
            break;
          case ParticipateAssetIssueContract:
            ParticipateAssetIssueContract participateAssetIssueContract = contract.getParameter()
                .unpack(ParticipateAssetIssueContract.class);
            addressList.add(
                ByteArray
                    .toHexString(participateAssetIssueContract.getOwnerAddress().toByteArray()));
            break;
          default:

        }
      } catch (InvalidProtocolBufferException e) {
        e.printStackTrace();
      }
    });
    return addressList;
  }

  @PostConstruct
  public void init() {
    addIndex(SuffixTreeIndex.onAttribute(BLOCK_ID));
    addIndex(NavigableIndex.onAttribute(BLOCK_NUMBER));
    addIndex(HashIndex.onAttribute(TRANSACTIONS));
    addIndex(NavigableIndex.onAttribute(WITNESS_ID));
    addIndex(SuffixTreeIndex.onAttribute(WITNESS_ADDRESS));
  }
}

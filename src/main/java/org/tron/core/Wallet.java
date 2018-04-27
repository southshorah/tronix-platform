/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.core;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.AccountList;
import org.tron.api.GrpcAPI.AssetIssueList;
import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.api.GrpcAPI.NumberMessage.Builder;
import org.tron.api.GrpcAPI.WitnessList;
import org.tron.common.application.Application;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.Hash;
import org.tron.common.overlay.message.Message;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.utils.Utils;
import org.tron.common.vm.program.InternalTransaction;
import org.tron.common.vm.program.ProgramResult;
import org.tron.core.actuator.TransactionExecutionSummary;
import org.tron.core.actuator.TransactionExecutor;
import org.tron.core.capsule.*;
import org.tron.core.db.*;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.exception.ValidateSignatureException;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.net.node.Node;
import org.tron.protos.Contract;
import org.tron.protos.Contract.AccountCreateContract;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Contract.ParticipateAssetIssueContract;
import org.tron.protos.Contract.TransferAssetContract;
import org.tron.protos.Contract.TransferContract;
import org.tron.protos.Contract.VoteWitnessContract;
import org.tron.protos.Contract.WitnessCreateContract;
import org.tron.protos.Contract.WitnessUpdateContract;
import org.tron.protos.Contract.ContractCreationContract;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.TXOutput;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class Wallet {

  private BlockStore db;
  @Getter
  private final ECKey ecKey;
  @Getter
  private UtxoStore utxoStore;
  private Application app;
  private Node p2pnode;
  private Manager dbManager;
  private static String addressPreFixString = Constant.ADD_PRE_FIX_STRING_TESTNET;  //default testnet
  private static byte addressPreFixByte = Constant.ADD_PRE_FIX_BYTE_TESTNET;

  /**
   * Creates a new Wallet with a random ECKey.
   */
  public Wallet() {
    this.ecKey = new ECKey(Utils.getRandom());
  }

  /**
   * constructor.
   */
  public Wallet(Application app) {
    this.app = app;
    this.p2pnode = app.getP2pNode();
    this.db = app.getBlockStoreS();
    utxoStore = app.getDbManager().getUtxoStore();
    dbManager = app.getDbManager();
    this.ecKey = new ECKey(Utils.getRandom());
  }

  /**
   * Creates a Wallet with an existing ECKey.
   */
  public Wallet(final ECKey ecKey) {
    this.ecKey = ecKey;
    logger.info("wallet address: {}", ByteArray.toHexString(this.ecKey.getAddress()));
  }

  public byte[] getAddress() {
    return ecKey.getAddress();
  }

  public static String getAddressPreFixString() {
    return addressPreFixString;
  }

  public static void setAddressPreFixString(String addressPreFixString) {
    Wallet.addressPreFixString = addressPreFixString;
  }

  public static byte getAddressPreFixByte() {
    return addressPreFixByte;
  }

  public static void setAddressPreFixByte(byte addressPreFixByte) {
    Wallet.addressPreFixByte = addressPreFixByte;
  }

  public static boolean addressValid(byte[] address) {
    if (address == null || address.length == 0) {
      logger.warn("Warning: Address is empty !!");
      return false;
    }
    if (address.length != Constant.ADDRESS_SIZE / 2) {
      logger.warn(
          "Warning: Address length need " + Constant.ADDRESS_SIZE + " but " + address.length
              + " !!");
      return false;
    }
    if (address[0] != addressPreFixByte) {
      logger.warn("Warning: Address need prefix with " + addressPreFixByte + " but "
          + address[0] + " !!");
      return false;
    }
    //Other rule;
    return true;
  }

  public static boolean addressValid(String addressStr) {
    if (addressStr == null || "".equals(addressStr)) {
      logger.warn("Warning: Address is empty !!");
      return false;
    }
    try {
      byte[] address = ByteArray.fromHexString(addressStr);
      return addressValid(address);
    } catch (Exception e) {
      logger.error(e.getMessage());
      return false;
    }
  }

  /**
   * Get balance by address.
   */
  public long getBalance(byte[] address) {
    long balance = utxoStore.findUtxo(address).stream().mapToLong(TXOutput::getValue).sum();
    logger.info("balance = {}", balance);
    return balance;
  }

  public Account getBalance(Account account) {
    AccountStore accountStore = dbManager.getAccountStore();
    AccountCapsule accountCapsule = accountStore.get(account.getAddress().toByteArray());
    return accountCapsule == null ? null : accountCapsule.getInstance();
  }

  /**
   * Create a transaction.
   */
  /*public Transaction createTransaction(byte[] address, String to, long amount) {
    long balance = getBalance(address);
    return new TransactionCapsule(address, to, amount, balance, utxoStore).getInstance();
  } */

  /**
   * Create a transaction by contract.
   */
  public Transaction createTransaction(TransferContract contract) {
    AccountStore accountStore = dbManager.getAccountStore();
    return new TransactionCapsule(contract, accountStore).getInstance();
  }

  /**
   * Broadcast a transaction.
   */
  public boolean broadcastTransaction(Transaction signaturedTransaction) {
    TransactionCapsule trx = new TransactionCapsule(signaturedTransaction);
    try {
      if (trx.validateSignature()) {
        Message message = new TransactionMessage(signaturedTransaction);
        dbManager.pushTransactions(trx);
        p2pnode.broadcast(message);
        return true;
      }
    } catch (ValidateSignatureException e) {
      logger.debug(e.getMessage(), e);
    } catch (ContractValidateException e) {
      logger.debug(e.getMessage(), e);
    } catch (ContractExeException e) {
      logger.debug(e.getMessage(), e);
    } catch (Exception e) {
      logger.debug(e.getMessage(), e);
    }
    return false;
  }

  public Transaction createAccount(AccountCreateContract contract) {
    AccountStore accountStore = dbManager.getAccountStore();
    return new TransactionCapsule(contract, accountStore).getInstance();
  }

  public Transaction createTransaction(VoteWitnessContract voteWitnessContract) {
    return new TransactionCapsule(voteWitnessContract).getInstance();
  }

  public Transaction createTransaction(AssetIssueContract assetIssueContract) {
    return new TransactionCapsule(assetIssueContract).getInstance();
  }

  public Transaction createTransaction(WitnessCreateContract witnessCreateContract) {
    return new TransactionCapsule(witnessCreateContract).getInstance();
  }

  public Transaction createTransaction(WitnessUpdateContract witnessUpdateContract) {
    return new TransactionCapsule(witnessUpdateContract).getInstance();
  }

  public Block getNowBlock() {
    Sha256Hash headBlockId = dbManager.getHeadBlockId();
    try {
      return dbManager.getBlockById(headBlockId).getInstance();
    } catch (BadItemException e) {
      logger.info(e.getMessage());
      return null;
    } catch (ItemNotFoundException e) {
      logger.info(e.getMessage());
      return null;
    }
  }

  public Block getBlockByNum(long blockNum) {
    Sha256Hash headBlockId = null;
    try {
      headBlockId = dbManager.getBlockIdByNum(blockNum);
    } catch (BadItemException e) {
      logger.info(e.getMessage());
    } catch (ItemNotFoundException e) {
      logger.info(e.getMessage());
    }
    try {
      return dbManager.getBlockById(headBlockId).getInstance();
    } catch (BadItemException e) {
      logger.info(e.getMessage());
      return null;
    } catch (ItemNotFoundException e) {
      logger.info(e.getMessage());
      return null;
    }
  }

  public AccountList getAllAccounts() {
    AccountList.Builder builder = AccountList.newBuilder();
    List<AccountCapsule> accountCapsuleList =
        dbManager.getAccountStore().getAllAccounts();
    accountCapsuleList.forEach(accountCapsule -> builder.addAccounts(accountCapsule.getInstance()));
    return builder.build();
  }

  public WitnessList getWitnessList() {
    WitnessList.Builder builder = WitnessList.newBuilder();
    List<WitnessCapsule> witnessCapsuleList = dbManager.getWitnessStore().getAllWitnesses();
    witnessCapsuleList
        .forEach(witnessCapsule -> builder.addWitnesses(witnessCapsule.getInstance()));
    return builder.build();
  }

  public Transaction createTransaction(TransferAssetContract transferAssetContract) {
    return new TransactionCapsule(transferAssetContract).getInstance();
  }

  public Transaction createTransaction(
      ParticipateAssetIssueContract participateAssetIssueContract) {
    return new TransactionCapsule(participateAssetIssueContract).getInstance();
  }

  public AssetIssueList getAssetIssueList() {
    AssetIssueList.Builder builder = AssetIssueList.newBuilder();
    dbManager.getAssetIssueStore().getAllAssetIssues()
        .forEach(issueCapsule -> builder.addAssetIssue(issueCapsule.getInstance()));
    return builder.build();
  }

  public AssetIssueList getAssetIssueByAccount(ByteString accountAddress) {
    if (accountAddress == null || accountAddress.size() == 0) {
      return null;
    }
    List<AssetIssueCapsule> assetIssueCapsuleList = dbManager.getAssetIssueStore()
        .getAllAssetIssues();
    AssetIssueList.Builder builder = AssetIssueList.newBuilder();
    assetIssueCapsuleList.stream()
        .filter(assetIssueCapsule -> assetIssueCapsule.getOwnerAddress().equals(accountAddress))
        .forEach(issueCapsule -> {
          builder.addAssetIssue(issueCapsule.getInstance());
        });
    return builder.build();
  }

  public AssetIssueContract getAssetIssueByName(ByteString assetName) {
    if (assetName == null || assetName.size() == 0) {
      return null;
    }
    List<AssetIssueCapsule> assetIssueCapsuleList = dbManager.getAssetIssueStore()
        .getAllAssetIssues();
    for (AssetIssueCapsule assetIssueCapsule : assetIssueCapsuleList) {
      if (assetName.equals(assetIssueCapsule.getName())) {
        return assetIssueCapsule.getInstance();
      }
    }
    return null;
  }

  public NumberMessage totalTransaction() {
    Builder builder = NumberMessage.newBuilder()
        .setNum(dbManager.getTransactionStore().getTotalTransactions());
    return builder.build();
  }

  public Transaction createContarct(ContractCreationContract contractCreationContract) {
    return new TransactionCapsule(contractCreationContract, Transaction.Contract.ContractType.ContractCreationContract)
            .getInstance();
  }

  /**
   *
   * @param data
   * @return
   */
  private byte[] getSelector(byte[] data) {
    if (data == null ||
            data.length < 4) {
      return null;
    }

    byte[] ret = new byte[4];
    System.arraycopy(data, 0, ret, 0, 4);
    return ret;
  }

  /**
   *
   * @param abi
   * @param selector
   * @return
   * @throws Exception
   */
  private boolean isConstant(ContractCreationContract.ABI abi, byte[] selector) throws Exception{
    if (selector == null || selector.length != 4) {
      throw new Exception("Selector's length or selector itself is invalid");
    }

    for (int i = 0; i < abi.getEntrysCount(); i++) {
      ContractCreationContract.ABI.Entry entry = abi.getEntrys(i);
      if (entry.getType() != ContractCreationContract.ABI.Entry.EntryType.Function) {
        continue;
      }

      int inputCount = entry.getInputsCount();
      StringBuffer sb = new StringBuffer();
      sb.append(entry.getName().toStringUtf8());
      sb.append("(");
      for (int k = 0; k < inputCount; k++) {
        ContractCreationContract.ABI.Entry.Param param = entry.getInputs(k);
        sb.append(param.getType().toStringUtf8());
        if (k + 1 < inputCount) {
          sb.append(",");
        }
      }
      sb.append(")");

      byte[] funcSelector = new byte[4];
      System.arraycopy(Hash.sha3(sb.toString().getBytes()), 0, funcSelector, 0, 4);
      if (Arrays.equals(funcSelector, selector)) {
        if (entry.getConstant() == true) {
          return true;
        } else {
          return false;
        }
      }
    }

    throw new Exception("There is no the selector!");
  }

  /**
   *
   * @param contractCallContract
   * @return
   */
  public Transaction callContract(Contract.ContractCallContract contractCallContract) {
    ContractStore contractStore = dbManager.getContractStore();
    byte[] contractAddress = contractCallContract.getContractAddress().toByteArray();
    ContractCreationContract.ABI abi = contractStore.getABI(contractAddress);
    if (abi == null) {
      return null;
    }

    try {
      byte[] selector = getSelector(contractCallContract.getData().toByteArray());
      if (selector == null) {
        return null;
      }

      Transaction trx = null;
      if (!isConstant(abi, selector)) {
        trx = new TransactionCapsule(contractCallContract, Transaction.Contract.ContractType.ContractCallContract)
                .getInstance();
      } else {
        TransactionCapsule trxCap = new TransactionCapsule(contractCallContract, Transaction.Contract.ContractType.ContractCallContract);

        TransactionExecutor executor = new TransactionExecutor(trxCap, trxCap.getInstance(), null,
                dbManager.getRepositoryImpl(), dbManager.getProgramInvokeFactory(), null,
                InternalTransaction.ExecuterType.ET_CONSTANT_TYPE).setConstantCall(true);
        //executor.withCommonConfig()
        executor.init();
        executor.execute();
        executor.go();

        ProgramResult programResult = executor.getResult();
        //TransactionExecutionSummary summary = executor.finalization();
        Transaction.Result.Builder builder = Transaction.Result.newBuilder();
        builder.setConstantResult(ByteString.copyFrom(programResult.getHReturn()));
        trx = trxCap.getInstance();
        trx = trx.toBuilder().addRet(builder.build()).build();
      }

      return trx;
    } catch (Exception e) {
      logger.error(e.getMessage());
      return null;
    }
  }

  public ContractCreationContract getContract(GrpcAPI.BytesMessage bytesMessage) {
    byte[] address = bytesMessage.getValue().toByteArray();
    AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
    if (accountCapsule == null || ArrayUtils.isEmpty(accountCapsule.getCodeHash())) {
      logger.error("Get contract failed, the account is not exist or the account does not have code hash!");
      return null;
    }

    ContractCapsule contractCapsule = dbManager.getContractStore().get(bytesMessage.getValue().toByteArray());
    Transaction trx = contractCapsule.getInstance();
    Any contract = trx.getRawData().getContract(0).getParameter();
    if (contract.is(ContractCreationContract.class)) {
      try {
        return contract.unpack(ContractCreationContract.class);
      } catch (InvalidProtocolBufferException e) {
        return null;
      }
    }
    return null;
  }
}

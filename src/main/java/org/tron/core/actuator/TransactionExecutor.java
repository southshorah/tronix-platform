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
package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.common.utils.ByteArraySet;
import org.tron.common.vm.LogInfo;
import org.tron.common.vm.PrecompiledContracts;
import org.tron.common.vm.VM;
import org.tron.common.vm.program.InternalTransaction;
import org.tron.common.vm.program.Program;
import org.tron.common.vm.program.ProgramResult;
import org.tron.common.vm.program.invoke.ProgramInvoke;
import org.tron.common.vm.program.invoke.ProgramInvokeFactory;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.config.CommonConfig;
import org.tron.core.config.SystemProperties;
import org.tron.core.db.*;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Contract.ContractCreationContract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;

import java.util.List;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.tron.core.actuator.TransactionExecutor.TrxType.TRX_CONTRACT_CALL_TYPE;
import static org.tron.core.actuator.TransactionExecutor.TrxType.TRX_UNKNOWN_TYPE;


public class TransactionExecutor {

    private static final Logger logger = LoggerFactory.getLogger("execute");
    // private static final Logger stateLogger = LoggerFactory.getLogger("state");

    SystemProperties config;
    CommonConfig commonConfig;

    private TransactionCapsule trxCap;
    private Transaction tx;
    private Repository track;
    private Repository cacheTrack;
    private Manager manager;
    private BlockStore blockStore;
    private ContractStore contractStore;
    private AccountStore accountStore;
    private boolean readyToExecute = false;
    private String execError;

    private ProgramInvokeFactory programInvokeFactory;
    private byte[] coinbase;

    // private TransactionReceipt receipt;
    private ProgramResult result = new ProgramResult();
    private Block currentBlock;

    // private final EthereumListener listener;

    private VM vm = null;
    private Program program = null;

    PrecompiledContracts.PrecompiledContract    precompiledContract;
    List<LogInfo> logs = null;
    private ByteArraySet touchedAccounts = new ByteArraySet();
    boolean localCall = false;

    enum TrxType{
        TRX_PRECOMPILED_TYPE,
        TRX_CONTRACT_CREATION_TYPE,
        TRX_CONTRACT_CALL_TYPE,
        TRX_UNKNOWN_TYPE,

    };
    private TrxType trxType = TRX_UNKNOWN_TYPE;

    private long gasEnd;

    public TransactionExecutor(TransactionCapsule trxCap, Transaction tx, byte[] coinbase, Repository track, Manager manager, BlockStore blockStore,
                               ContractStore contractStore, AccountStore accountStore, ProgramInvokeFactory programInvokeFactory, Block currentBlock) {

        this(trxCap, tx, coinbase, track, blockStore, manager, contractStore, accountStore, programInvokeFactory, currentBlock, 0);
    }

    public TransactionExecutor(TransactionCapsule trxCap, Transaction tx, byte[] coinbase, Repository track, BlockStore blockStore, Manager manager,
                               ContractStore contractStore, AccountStore accountStore, ProgramInvokeFactory programInvokeFactory, Block currentBlock, long gasUsedInTheBlock) {
        this.trxCap = trxCap;
        this.tx = tx;
        this.coinbase = coinbase;
        this.track = track;
        //this.cacheTrack = track.startTracking();
        this.cacheTrack = track;
        this.manager = manager;
        this.blockStore = blockStore;
        this.contractStore = contractStore;
        this.accountStore = accountStore;
        this.programInvokeFactory = programInvokeFactory;
        this.currentBlock = currentBlock;
        // this.listener = listener;
        Transaction.Contract.ContractType contractType = tx.getRawData().getContract(0).getType();
        switch (contractType.getNumber()) {
            case Transaction.Contract.ContractType.ContractCallContract_VALUE:
                trxType = TrxType.TRX_CONTRACT_CALL_TYPE;
                break;
            case Transaction.Contract.ContractType.ContractCreationContract_VALUE:
                trxType = TrxType.TRX_CONTRACT_CREATION_TYPE;
                break;
            default:
                trxType = TrxType.TRX_PRECOMPILED_TYPE;

        }

        withCommonConfig(CommonConfig.getDefault());
    }

    public TransactionExecutor withCommonConfig(CommonConfig commonConfig) {
        this.commonConfig = commonConfig;
        this.config = commonConfig.systemProperties();
        //this.blockchainConfig = config.getBlockchainConfig().getConfigForBlock(currentBlock.getNumber());
        return this;
    }

    private void execError(String err) {
        logger.warn(err);
        execError = err;
    }

    /**
     * Do all the basic validation, if the executor
     * will be ready to run the transaction at the end
     * set readyToExecute = true
     */
    public void init() {
        if (localCall) {
            readyToExecute = true;
            return;
        }
        readyToExecute = true;
    }

    public void precompiled() throws ContractValidateException, ContractExeException{
        //try {
            final List<Actuator> actuatorList = ActuatorFactory.createActuator(this.trxCap, this.manager);
            TransactionResultCapsule ret = new TransactionResultCapsule();
            for (Actuator act : actuatorList) {
                act.validate();
                act.execute(ret);
                trxCap.setResult(ret);
            }
        //} catch (ContractValidateException e) {

        //} catch (ContractExeException e) {

        //}
    }

    public void execute() throws ContractValidateException, ContractExeException{
        if (!readyToExecute) return;
        switch (trxType) {
            case TRX_PRECOMPILED_TYPE:
                precompiled();
                break;
            case TRX_CONTRACT_CREATION_TYPE:
                create();
                break;
            case TRX_CONTRACT_CALL_TYPE:
                call();
                break;
            default:
                break;
        }
    }

    private Contract.ContractCallContract getContractCallContract(Any contract) {
        try {
            Contract.ContractCallContract contractCallContract = contract.unpack(Contract.ContractCallContract.class);
            return contractCallContract;
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    private void call() {
        {
            // TODO: Security Block Chain (2018.04)
        }
        if (!readyToExecute) return;
        gasEnd = 1024;
        Any callContract = tx.getRawData().getContract(0).getParameter();
        Contract.ContractCallContract contractCallContract = getContractCallContract(callContract);
        if (contractCallContract == null) return;

        ByteString contractAddress = contractCallContract.getContractAddress();
        ContractCapsule contractCapsule = this.contractStore.get(contractAddress.toByteArray());
        Any creationContact = contractCapsule.getInstance().getRawData().getContract(0).getParameter();
        ContractCreationContract contractCreationContract = getContractCreationContract(creationContact);
        byte[] code = contractCreationContract.getBytecode().toByteArray();


        if (isEmpty(code)) {
            //m_endGas = m_endGas.subtract(BigInteger.valueOf(basicTxCost));
            //result.spendGas(basicTxCost);
        } else {
            ProgramInvoke programInvoke =
                    programInvokeFactory.createProgramInvoke(tx, currentBlock, cacheTrack, blockStore);
            this.vm = new VM(config);
            // this.program = new Program(track.getCodeHash(targetAddress), code, programInvoke, tx, config).withCommonConfig(commonConfig);
            this.program = new Program(contractCapsule.getCodeHash().getBytes(), code, programInvoke, new InternalTransaction(tx), config).withCommonConfig(commonConfig);
        }


        //BigInteger endowment = toBI(tx.getValue());
        //transfer(cacheTrack, tx.getSender(), targetAddress, endowment);

        //touchedAccounts.add(targetAddress);
    }

    private ContractCreationContract getContractCreationContract(Any contract) {
        try {
            ContractCreationContract contractCreationContract = contract.unpack(ContractCreationContract.class);
            return contractCreationContract;
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    private void create() {
        {
            // TODO: Security Block Chain (2018.04)
        }
        // Create the Contract Account
        Any contract = tx.getRawData().getContract(0).getParameter();
        ContractCreationContract contractCreationContract = getContractCreationContract(contract);
        ByteString newContractAddress = contractCreationContract.getContractAddress();
        if (!this.accountStore.has(newContractAddress.toByteArray())) {
            AccountCapsule account = new AccountCapsule(newContractAddress,
                    Protocol.AccountType.Contract);
            this.accountStore.put(newContractAddress.toByteArray(), account);
        } else {
            logger.debug("new contract address has exist!", newContractAddress.toString());
            return;
        }

        // Store the Transaction, which Represent the Contract
        this.contractStore.put(newContractAddress.toByteArray(), new ContractCapsule(tx));

    }

    public void go() {
        {
            // TODO: Security Block Chain (2018.04)
        }
        if (!readyToExecute) return;
        if (trxType != TRX_CONTRACT_CALL_TYPE) return;

        /*
        try {
            if (vm != null) {
                // Charge basic cost of the transaction
                program.spendGas(tx.transactionCost(config.getBlockchainConfig(), currentBlock), "TRANSACTION COST");

                if (config.playVM())
                    vm.play(program);

                result = program.getResult();
                m_endGas = toBI(tx.getGasLimit()).subtract(toBI(program.getResult().getGasUsed()));

                if (tx.isContractCreation() && !result.isRevert()) {
                    int returnDataGasValue = getLength(program.getResult().getHReturn()) *
                            blockchainConfig.getGasCost().getCREATE_DATA();
                    if (m_endGas.compareTo(BigInteger.valueOf(returnDataGasValue)) < 0) {
                        // Not enough gas to return contract code
                        if (!blockchainConfig.getConstants().createEmptyContractOnOOG()) {
                            program.setRuntimeFailure(Program.Exception.notEnoughSpendingGas("No gas to return just created contract",
                                    returnDataGasValue, program));
                            result = program.getResult();
                        }
                        result.setHReturn(EMPTY_BYTE_ARRAY);
                    } else if (getLength(result.getHReturn()) > blockchainConfig.getConstants().getMAX_CONTRACT_SZIE()) {
                        // Contract size too large
                        program.setRuntimeFailure(Program.Exception.notEnoughSpendingGas("Contract size too large: " + getLength(result.getHReturn()),
                                returnDataGasValue, program));
                        result = program.getResult();
                        result.setHReturn(EMPTY_BYTE_ARRAY);
                    } else {
                        // Contract successfully created
                        m_endGas = m_endGas.subtract(BigInteger.valueOf(returnDataGasValue));
                        cacheTrack.saveCode(tx.getContractAddress(), result.getHReturn());
                    }
                }

                String err = config.getBlockchainConfig().getConfigForBlock(currentBlock.getNumber()).
                        validateTransactionChanges(blockStore, currentBlock, tx, null);
                if (err != null) {
                    program.setRuntimeFailure(new RuntimeException("Transaction changes validation failed: " + err));
                }


                if (result.getException() != null || result.isRevert()) {
                    result.getDeleteAccounts().clear();
                    result.getLogInfoList().clear();
                    result.resetFutureRefund();
                    rollback();

                    if (result.getException() != null) {
                        throw result.getException();
                    } else {
                        execError("REVERT opcode executed");
                    }
                } else {
                    touchedAccounts.addAll(result.getTouchedAccounts());
                    cacheTrack.commit();
                }

            } else {
                cacheTrack.commit();
            }

        } catch (Throwable e) {

            // TODO: catch whatever they will throw on you !!!
//            https://github.com/ethereum/cpp-ethereum/blob/develop/libethereum/Executive.cpp#L241
            rollback();
            execError(e.getMessage());
        }
        */
    }

    private void rollback() {

        cacheTrack.rollback();

        /*
        // remove touched account
        touchedAccounts.remove(
                tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress());
                */
    }

    public TransactionExecutionSummary finalization() {
        if (!readyToExecute) return null;

        /*
        TransactionExecutionSummary.Builder summaryBuilder = TransactionExecutionSummary.builderFor(tx)
                .gasLeftover(m_endGas)
                .logs(result.getLogInfoList())
                .result(result.getHReturn());

        if (result != null) {
            // Accumulate refunds for suicides
            result.addFutureRefund(result.getDeleteAccounts().size() * config.getBlockchainConfig().
                    getConfigForBlock(currentBlock.getNumber()).getGasCost().getSUICIDE_REFUND());
            long gasRefund = Math.min(result.getFutureRefund(), getGasUsed() / 2);
            byte[] addr = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();
            m_endGas = m_endGas.add(BigInteger.valueOf(gasRefund));

            summaryBuilder
                    .gasUsed(toBI(result.getGasUsed()))
                    .gasRefund(toBI(gasRefund))
                    .deletedAccounts(result.getDeleteAccounts())
                    .internalTransactions(result.getInternalTransactions());

            ContractDetails contractDetails = track.getContractDetails(addr);
            if (contractDetails != null) {
                // TODO
//                summaryBuilder.storageDiff(track.getContractDetails(addr).getStorage());
//
//                if (program != null) {
//                    summaryBuilder.touchedStorage(contractDetails.getStorage(), program.getStorageDiff());
//                }
            }

            if (result.getException() != null) {
                summaryBuilder.markAsFailed();
            }
        }

        TransactionExecutionSummary summary = summaryBuilder.build();

        // Refund for gas leftover
        track.addBalance(tx.getSender(), summary.getLeftover().add(summary.getRefund()));
        logger.info("Pay total refund to sender: [{}], refund val: [{}]", Hex.toHexString(tx.getSender()), summary.getRefund());

        // Transfer fees to miner
        track.addBalance(coinbase, summary.getFee());
        touchedAccounts.add(coinbase);
        logger.info("Pay fees to miner: [{}], feesEarned: [{}]", Hex.toHexString(coinbase), summary.getFee());

        if (result != null) {
            logs = result.getLogInfoList();
            // Traverse list of suicides
            for (DataWord address : result.getDeleteAccounts()) {
                track.delete(address.getLast20Bytes());
            }
        }

        if (blockchainConfig.eip161()) {
            for (byte[] acctAddr : touchedAccounts) {
                AccountState state = track.getAccountState(acctAddr);
                if (state != null && state.isEmpty()) {
                    track.delete(acctAddr);
                }
            }
        }


        listener.onTransactionExecuted(summary);

        if (config.vmTrace() && program != null && result != null) {
            String trace = program.getTrace()
                    .result(result.getHReturn())
                    .error(result.getException())
                    .toString();


            if (config.vmTraceCompressed()) {
                trace = zipAndEncode(trace);
            }

            String txHash = toHexString(tx.getHash());
            saveProgramTraceFile(config, txHash, trace);
            listener.onVMTraceCreated(txHash, trace);
        }
        return summary;
        */
        return null;
    }

    public TransactionExecutor setLocalCall(boolean localCall) {
        this.localCall = localCall;
        return this;
    }

    /*
    public TransactionReceipt getReceipt() {
        if (receipt == null) {
            receipt = new TransactionReceipt();
            long totalGasUsed = gasUsedInTheBlock + getGasUsed();
            receipt.setCumulativeGas(totalGasUsed);
            receipt.setTransaction(tx);
            receipt.setLogInfoList(getVMLogs());
            receipt.setGasUsed(getGasUsed());
            receipt.setExecutionResult(getResult().getHReturn());
            receipt.setError(execError);
//            receipt.setPostTxState(track.getRoot()); // TODO later when RepositoryTrack.getRoot() is implemented
        }
        return receipt;
    }
    */

    public List<LogInfo> getVMLogs() {
        return logs;
    }

    public ProgramResult getResult() {
        return result;
    }

    public long getGasUsed() {
        //return toBI(tx.getGasLimit()).subtract(m_endGas).longValue();
        return 0;
    }

}

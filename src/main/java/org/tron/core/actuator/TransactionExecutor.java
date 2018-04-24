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
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.Hash;
import org.tron.common.utils.ByteArraySet;
import org.tron.common.utils.Utils;
import org.tron.common.vm.LogInfo;
import org.tron.common.vm.PrecompiledContracts;
import org.tron.common.vm.VM;
import org.tron.common.vm.program.InternalTransaction;
import org.tron.common.vm.program.Program;
import org.tron.common.vm.program.ProgramPrecompile;
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
import org.tron.security.SecurityFactory;
import java.util.List;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.tron.common.vm.program.InternalTransaction.TrxType;
import static org.tron.common.vm.program.InternalTransaction.TrxType.TRX_UNKNOWN_TYPE;
import static org.tron.common.vm.program.InternalTransaction.TrxType.TRX_CONTRACT_CALL_TYPE;
import static org.tron.common.vm.program.InternalTransaction.TrxType.TRX_CONTRACT_CREATION_TYPE;
import static org.tron.common.vm.program.InternalTransaction.TrxType.TRX_PRECOMPILED_TYPE;
import static org.tron.common.vm.program.InternalTransaction.ExecuterType;
import static org.tron.common.vm.program.InternalTransaction.ExecuterType.*;

/*
 * # # # #
 */
public class TransactionExecutor {

    private static final Logger logger = LoggerFactory.getLogger("execute");
    // private static final Logger stateLogger = LoggerFactory.getLogger("state");

    SystemProperties config;
    CommonConfig commonConfig;

    private TransactionCapsule trxCap;
    private Transaction tx;
    private RepositoryImpl track;
    //private Repository cacheTrack;
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
    boolean constantCall = false;


    private TrxType trxType = TRX_UNKNOWN_TYPE;
    private ExecuterType executerType = ET_UNKNOWN_TYPE;

    public TransactionExecutor(TransactionCapsule trxCap, Transaction tx, byte[] coinbase, RepositoryImpl track,
                               ProgramInvokeFactory programInvokeFactory, Block currentBlock) {

        this(trxCap, tx, coinbase, track, programInvokeFactory, currentBlock, 0);
    }

    public TransactionExecutor(TransactionCapsule trxCap, Transaction tx, byte[] coinbase, RepositoryImpl track,
                               ProgramInvokeFactory programInvokeFactory, Block currentBlock, long gasUsedInTheBlock) {
        this.trxCap = trxCap;
        this.tx = tx;
        this.coinbase = coinbase;  // may be null
        this.track = track;
        //this.cacheTrack = track.startTracking();
        this.programInvokeFactory = programInvokeFactory;
        this.currentBlock = currentBlock;  // may be null
        this.executerType = currentBlock == null ? ET_PRE_TYPE : ET_NORMAL_TYPE;
        // this.listener = listener;
        Transaction.Contract.ContractType contractType = tx.getRawData().getContract(0).getType();
        switch (contractType.getNumber()) {
            case Transaction.Contract.ContractType.ContractCallContract_VALUE:
                trxType = TRX_CONTRACT_CALL_TYPE;
                break;
            case Transaction.Contract.ContractType.ContractCreationContract_VALUE:
                trxType = TRX_CONTRACT_CREATION_TYPE;
                break;
            default:
                trxType = TRX_PRECOMPILED_TYPE;

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
        if (constantCall) {
            readyToExecute = true;
            return;
        }
        readyToExecute = true;
    }

    private boolean doNext() {
        if (executerType == ET_NORMAL_TYPE) {
            return true;
        }

        return false;
    }

    public void precompiled() throws ContractValidateException, ContractExeException{
        //try {
            final List<Actuator> actuatorList = ActuatorFactory.createActuator(this.trxCap, this.track.getDbManager());
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

        if (!doNext()) return;

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
        Contract.ContractCallContract contract = ContractCapsule.getCallContractFromTransaction(tx);
        if (contract == null) return;

        byte[] contractAddress = contract.getContractAddress().toByteArray();
        byte[] code = this.track.getCode(contractAddress);
        if (isEmpty(code)) {

        } else {
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(TRX_CONTRACT_CALL_TYPE, executerType, tx,
                    currentBlock, track, track.getBlockStore());
            this.vm = new VM(config);
            // this.program = new Program(track.getCodeHash(targetAddress), code, programInvoke, tx, config).withCommonConfig(commonConfig);
            this.program = new Program(track.getCodeHash(contractAddress), code, programInvoke,
                    new InternalTransaction(tx), config).withCommonConfig(commonConfig);
        }


        //BigInteger endowment = toBI(tx.getValue());
        //transfer(cacheTrack, tx.getSender(), targetAddress, endowment);

        //touchedAccounts.add(targetAddress);
    }

    /*
     **/
    private void create() {
        ContractCreationContract contract = ContractCapsule.getCreationContractFromTransaction(tx);
        // Security Block Chain (2018.04)
        if (!SecurityFactory.getInstance().validateContract(Any.pack(contract))) {
            return;
        }

        // Create a Contract Account by ownerAddress or If the address exist, random generate one
        byte[] code = contract.getBytecode().toByteArray();
        ContractCreationContract.ABI abi = contract.getAbi();
        byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
        byte[] newContractAddress;
        if (contract.getContractAddress() == null) {
            byte[] privKey = Hash.sha256(ownerAddress);
            ECKey ecKey = ECKey.fromPrivate(privKey);
            newContractAddress = ecKey.getAddress();
            while (true) {
                AccountCapsule existingAddr = this.track.getAccountCapsule(newContractAddress);
                // if (existingAddr == null || existingAddr.getCodeHash().length == 0) {
                if (existingAddr == null) {
                    break;
                }

                ecKey = new ECKey(Utils.getRandom());
                newContractAddress = ecKey.getAddress();
            }
        } else {
            newContractAddress = contract.getContractAddress().toByteArray();
        }

        // crate vm to constructor smart contract
        try {
            byte[] ops = contract.getBytecode().toByteArray();
            InternalTransaction internalTransaction = new InternalTransaction(tx);
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(TRX_CONTRACT_CREATION_TYPE, executerType, tx,
                    currentBlock, track, track.getBlockStore());
            this.vm = new VM(config);
            this.program = new Program(ops, programInvoke, internalTransaction, config).withCommonConfig(commonConfig);
        } catch(Exception e) {
            logger.error(e.getMessage());
            execError = e.getMessage();
            return;
        }

        // Store the account, code and contract
        this.track.createAccount(newContractAddress, Protocol.AccountType.Contract);
        this.track.saveContract(newContractAddress, new ContractCapsule(tx));
        this.track.saveCode(newContractAddress, ProgramPrecompile.getCode(code));
    }

    public void go() {
        {/* TODO: Security Block Chain (2018.04) */}
        if (!readyToExecute) return;
        if (trxType != TRX_CONTRACT_CALL_TYPE && trxType != TRX_CONTRACT_CREATION_TYPE) return;
        if (!doNext()) return;

        try {
            if (vm != null) {
                { /* charge gas for trx ? */}

                if (config.vmOn()) {
                    vm.play(program);
                }

                result = program.getResult();
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
                    track.commit();
                }

            } else {
                track.commit();
            }
        } catch(Exception e) {
            logger.error(e.getMessage());
            // rollback;
            rollback();
            execError(e.getMessage());
        }
    }

    private void rollback() {

        //cacheTrack.rollback();

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

    public TransactionExecutor setConstantCall(boolean constantCall) {
        this.constantCall = constantCall;
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

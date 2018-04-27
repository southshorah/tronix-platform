package org.tron.common.vmstorage;

import org.tron.core.capsule.TransactionCapsule;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Guo Yonggang
 * @since 27.04.2018
 */
public class DepositController {

    public enum DepositMode {
        DEPOSIT_MODE_NORMAL,
        DEPOSIT_MODE_SYNC,
        DEPOSIT_MODE_UNKNOWN,
    };

    private static int MAX_QUEUE_SIZE = 21;
    private Queue<DepositImpl> depositQueue = new LinkedList<>();


    private DepositController() {}

    public boolean preProcessTransaction(TransactionCapsule trxCap) {

        return true;
    }
    /**
     * Single instance
     */
    private static DepositController instance = new DepositController();
    public static DepositController getInstance() {
        return instance;
    }
}

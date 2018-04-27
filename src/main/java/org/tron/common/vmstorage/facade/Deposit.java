package org.tron.common.vmstorage.facade;

import org.tron.common.vm.DataWord;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Guo Yonggang
 * @since 2018.04.27
 */

public interface Deposit {

    /**
     *
     * @param addr
     * @return
     */
    long getBalance(byte[] addr);

    /**
     *
     * @param addr
     * @return
     */
    byte[] getCode(byte[] addr);
}

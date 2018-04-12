package org.tron.core.db;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.tron.core.capsule.ProposalCapsule;

@Slf4j
public class ProposalStore extends TronStoreWithRevoking<ProposalCapsule> {

  protected ProposalStore(String dbName) {
    super(dbName);
  }

  @Override
  public ProposalCapsule get(byte[] key) {
    byte[] value = dbSource.getData(key);
    return ArrayUtils.isEmpty(value) ? null : new ProposalCapsule(value);
  }

  @Override
  public boolean has(byte[] key) {
    byte[] account = dbSource.getData(key);
    logger.info("address is {},witness is {}", key, account);
    return null != account;
  }

  private static ProposalStore instance;

  public static void destory() {
    instance = null;
  }

  /**
   * create fun.
   *
   * @param dbName the name of database
   */
  public static ProposalStore create(String dbName) {
    if (instance == null) {
      synchronized (UtxoStore.class) {
        if (instance == null) {
          instance = new ProposalStore(dbName);
        }
      }
    }
    return instance;
  }

  /**
   * get all Proposals.
   */
  public List<ProposalCapsule> getAllProposals() {
    return dbSource.allValues().stream().map(bytes ->
        new ProposalCapsule(bytes)
    ).collect(Collectors.toList());
  }

}

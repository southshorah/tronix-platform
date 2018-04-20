package org.tron.core.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.tron.core.capsule.FreezeAccountCapsule;

@Slf4j
public class FreezeAccountStore extends TronStoreWithRevoking<FreezeAccountCapsule> {

  private static Map<String, byte[]> assertsAddress = new HashMap<String, byte[]>();  //key = name , value = address
  private static FreezeAccountStore instance;


  private FreezeAccountStore(String dbName) {
    super(dbName);
  }

  public static void destroy() {
    instance = null;
  }

  /**
   * create fun.
   *
   * @param dbName the name of database
   */
  public static FreezeAccountStore create(String dbName) {
    if (instance == null) {
      synchronized (FreezeAccountStore.class) {
        if (instance == null) {
          instance = new FreezeAccountStore(dbName);
        }
      }
    }
    return instance;
  }

  @Override
  public FreezeAccountCapsule get(byte[] key) {
    byte[] value = dbSource.getData(key);
    return ArrayUtils.isEmpty(value) ? null : new FreezeAccountCapsule(value);
  }

  /**
   * isFreezeAccountExist
   *
   * @param key the address of FreezeAccount
   */
  @Override
  public boolean has(byte[] key) {
    byte[] account = dbSource.getData(key);
    return null != account;
  }

  /**
   * get all freezeAccounts.
   */
  public List<FreezeAccountCapsule> getAllFreezeAccounts() {
    return dbSource.allValues().stream().map(bytes ->
        new FreezeAccountCapsule(bytes)
    ).collect(Collectors.toList());
  }

}

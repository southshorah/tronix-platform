package org.tron.security.contract;

import com.google.protobuf.Any;
import lombok.extern.slf4j.Slf4j;
import org.tron.security.Security;

@Slf4j
public class ContractSecurity implements Security {

  ContractSecurity() {
    super();
  }

  @Override
  public boolean validateContract(Any contract) {
    return true;
  }

}

package org.tron.security;

import com.google.protobuf.Any;

public interface Security {

  boolean validateContract(Any contract);
}

package org.tron.program;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tron.core.Constant;
import org.tron.core.config.Configuration;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;

@Slf4j
public class FullNode {
  /**
   * Start the FullNode.
   */
  public static void main(String[] args) throws InterruptedException {
    Args.setParam(args, Configuration.getByPath(Constant.NORMAL_CONF));
    ApplicationContext context = new AnnotationConfigApplicationContext(DefaultConfig.class);
    while (true) {
      Thread.sleep(10000);
    }
  }
}

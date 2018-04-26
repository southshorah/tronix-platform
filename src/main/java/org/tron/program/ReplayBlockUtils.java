package org.tron.program;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tron.common.utils.FileUtil;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.BlockStore;
import org.tron.core.db.Manager;
import org.tron.core.exception.*;
import java.nio.file.Paths;
import java.util.Iterator;


@Slf4j
public class ReplayBlockUtils {

//    static {
//        Args.setParam(new String[] {}, "config-beta.conf");
//    }

    private static void backupAndCleanDb(String dataBaseDir) {

        dataBaseDir += "/database";
        String[] dbs = new String[] {
                "account",
                "asset-issue",
                "block-index",
                "block_KDB",
                "peers",
                "trans",
                "utxo",
                "witness",
                "witness_schedule",
                "nodeId.properties"
        };

        for (String db: dbs) {
            System.out.println(Paths.get(dataBaseDir, db).toString());
            FileUtil.recursiveDelete(Paths.get(dataBaseDir, db).toString());
        }
    }

    public static void main(String[] args) throws BadBlockException {
        Args.setParam(new String[] {}, "config-beta.conf");

        ApplicationContext context = new AnnotationConfigApplicationContext(DefaultConfig.class);
        Manager dbManager = context.getBean(Manager.class);
        String dataBaseDir = Args.getInstance().getLocalDBDirectory();

        replayBlock(dbManager, dataBaseDir);
    }

    public static void replayBlock(Manager dbManager, String dataBaseDiir) throws BadBlockException {
        backupAndCleanDb(dataBaseDiir);


        long latestSolidifiedBlockNum = dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
        BlockStore localBlockStore = dbManager.getBlockStore();

        logger.info(String.format("latestSolidifiedBlockNum is %d", latestSolidifiedBlockNum));
        logger.info(String.format("%d local block to replay", latestSolidifiedBlockNum));

        dbManager.resetDynamicProperties();

        Iterator iterator = localBlockStore.iterator();
        long replayIndex = 0;
        while (iterator.hasNext() && replayIndex <= latestSolidifiedBlockNum) {
            BlockCapsule blockCapsule = (BlockCapsule) iterator.next();
            if (replayIndex == 0) {
                // skip Genesis Block
                replayIndex++;
                continue;
            }
            try {
                dbManager.replayBlock(blockCapsule);
                dbManager.getDynamicPropertiesStore()
                        .saveLatestSolidifiedBlockNum(replayIndex);
            } catch (ValidateSignatureException e) {
                throw new BadBlockException("validate signature exception");
            } catch (ContractValidateException e) {
                throw new BadBlockException("validate contract exception");
            } catch (UnLinkedBlockException e) {
                throw new BadBlockException("validate unlink exception");
            } catch (ContractExeException e) {
                throw new BadBlockException("validate contract exe exception");
            }
            replayIndex++;
        }
        logger.info(String.format("replay local block complete, replay %d blocks, include Genesis", replayIndex-1));
    }

    public static void cleanLocalDb() {

    }
}

package org.tron.program;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.BlockStore;
import org.tron.core.db.DynamicPropertiesStore;
import org.tron.core.db.Manager;
import org.tron.core.exception.*;
import org.tron.core.services.RpcApiService;

import java.io.File;
import java.nio.file.Paths;
import java.util.Iterator;


@Slf4j
public class ReplayBlockUtils {

    static {
        Args.setParam(new String[] {}, "config-beta.conf");
    }

    private static void backupAndCleanDb() {
        String dataBaseDir = Args.getInstance().getOutputDirectory() + "/database";
//        File blockDBFile = new File(dataBaseDir, "block");
//        blockDBFile.renameTo(new File(dataBaseDir, "local_block"));
//
//        File propertyDBFile = new File(dataBaseDir, "properties");
//        propertyDBFile.renameTo(new File(dataBaseDir, "local_properties"));

        String[] dbs = new String[] {
                "account",
                "asset-issue",
//                "block",
                "block-index",
                "block_KDB",
                "peers",
//                "properties",
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

    public static void test() {

        ApplicationContext context = new AnnotationConfigApplicationContext(DefaultConfig.class);
        Manager dbManager = context.getBean(Manager.class);

        for (Iterator iterator = dbManager.getBlockStore().iterator(); iterator.hasNext();) {
            BlockCapsule blockCapsule = (BlockCapsule) iterator.next();
            System.err.println(blockCapsule);
        }

        for (Iterator iterator = dbManager.getAccountStore().iterator(); iterator.hasNext();) {
            System.err.println(iterator.next());
        }

        System.err.println(dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
        System.err.println(dbManager.getDynamicPropertiesStore().getBlockFilledSlotsIndex());
        System.err.println(dbManager.getDynamicPropertiesStore().getNextMaintenanceTime());

        for (Iterator iterator = dbManager.getAssetIssueStore().iterator(); iterator.hasNext();) {
            System.err.println(iterator.next());
        }

        for (Iterator iterator = dbManager.getPeersStore().iterator(); iterator.hasNext();) {
            System.err.println(iterator.next());
        }

        for (Iterator iterator = dbManager.getTransactionStore().iterator(); iterator.hasNext();) {
            System.err.println(iterator.next());
        }
    }


    public static void main(String[] args) throws BadItemException, ItemNotFoundException, ContractExeException, UnLinkedBlockException, ValidateScheduleException, ContractValidateException, ValidateSignatureException, InterruptedException {
//        backupAndCleanDb();
        replayBlock();
//        test();




    }

    private static void replayBlock() throws ValidateSignatureException, ContractValidateException, ContractExeException, UnLinkedBlockException, ValidateScheduleException, InterruptedException, BadItemException, ItemNotFoundException {
        ApplicationContext context = new AnnotationConfigApplicationContext(DefaultConfig.class);
        Manager dbManager = context.getBean(Manager.class);
//        BlockStore localBlockStore = new BlockStore("local_block");
//        DynamicPropertiesStore dynamicPropertiesStore = new DynamicPropertiesStore("local_properties");

        BlockStore localBlockStore = dbManager.getBlockStore();
        DynamicPropertiesStore dynamicPropertiesStore = dbManager.getDynamicPropertiesStore();



        long localLatestNum = dynamicPropertiesStore.getLatestSolidifiedBlockNum();
        long curLatestNum = dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
        long iii = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();

        System.err.println("localLatestNum="+localLatestNum);
        System.err.println("curLatestNum="+curLatestNum);
        System.err.println("LatestHeaderNumber="+iii);

//        dynamicPropertiesStore.reset();

//        for (long j = 0; j < 100; j++) {
//
//
//            System.err.println(dbManager.getBlockByNum(j));
//        }


        Iterator iter = localBlockStore.iterator();

        long replayIndex = 0;
        while (iter.hasNext() && replayIndex <= localLatestNum) {
            BlockCapsule blockCapsule = (BlockCapsule) iter.next();
//            if (replayIndex <= curLatestNum) {
//                replayIndex++;
//                continue;
//            }
            dbManager.pushBlock((blockCapsule));

            System.err.println(blockCapsule);
            dbManager.getDynamicPropertiesStore()
                    .saveLatestSolidifiedBlockNum(replayIndex);

            replayIndex++;
        }
    }
}

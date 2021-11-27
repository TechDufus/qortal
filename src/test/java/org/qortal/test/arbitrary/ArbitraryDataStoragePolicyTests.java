package org.qortal.test.arbitrary;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataTransactionBuilder;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager.StoragePolicy;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.Method;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.utils.Base58;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ArbitraryDataStoragePolicyTests extends Common {

    @Before
    public void beforeTest() throws DataException, InterruptedException {
        Common.useDefaultSettings();
        this.deleteListsDirectory();
        ArbitraryDataStorageManager.getInstance().start();

        // Wait for storage space to be calculated
        while (!ArbitraryDataStorageManager.getInstance().isStorageCapacityCalculated()) {
            Thread.sleep(100L);
        }
    }

    @After
    public void afterTest() throws DataException {
        this.deleteListsDirectory();
        ArbitraryDataStorageManager.getInstance().shutdown();
    }

    @Test
    public void testFollowedAndViewed() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "Test";

            // Register the name to Alice
            TransactionUtils.signAndMint(repository, new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, ""), alice);

            // Create transaction
            ArbitraryTransactionData transactionData = this.createTxnWithName(repository, alice, name);

            // Add name to followed list
            assertTrue(ResourceListManager.getInstance().addToList("followed", "names", name, false));

            // We should store and pre-fetch data for this transaction
            assertEquals(StoragePolicy.FOLLOWED_AND_VIEWED, Settings.getInstance().getStoragePolicy());
            assertTrue(storageManager.canStoreData(transactionData));
            assertTrue(storageManager.shouldPreFetchData(transactionData));

            // Now unfollow the name
            assertTrue(ResourceListManager.getInstance().removeFromList("followed", "names", name, false));

            // We should store but not pre-fetch data for this transaction
            assertTrue(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));
        }
    }

    @Test
    public void testFollowedOnly() throws DataException, IllegalAccessException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "Test";

            // Set the storage policy to "FOLLOWED"
            FieldUtils.writeField(Settings.getInstance(), "storagePolicy", "FOLLOWED", true);

            // Register the name to Alice
            TransactionUtils.signAndMint(repository, new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, ""), alice);

            // Create transaction
            ArbitraryTransactionData transactionData = this.createTxnWithName(repository, alice, name);

            // Add name to followed list
            assertTrue(ResourceListManager.getInstance().addToList("followed", "names", name, false));

            // We should store and pre-fetch data for this transaction
            assertEquals(StoragePolicy.FOLLOWED, Settings.getInstance().getStoragePolicy());
            assertTrue(storageManager.canStoreData(transactionData));
            assertTrue(storageManager.shouldPreFetchData(transactionData));

            // Now unfollow the name
            assertTrue(ResourceListManager.getInstance().removeFromList("followed", "names", name, false));

            // We shouldn't store or pre-fetch data for this transaction
            assertFalse(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));
        }
    }

    @Test
    public void testViewedOnly() throws DataException, IllegalAccessException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "Test";

            // Set the storage policy to "VIEWED"
            FieldUtils.writeField(Settings.getInstance(), "storagePolicy", "VIEWED", true);

            // Register the name to Alice
            TransactionUtils.signAndMint(repository, new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, ""), alice);

            // Create transaction
            ArbitraryTransactionData transactionData = this.createTxnWithName(repository, alice, name);

            // Add name to followed list
            assertTrue(ResourceListManager.getInstance().addToList("followed", "names", name, false));

            // We should store but not pre-fetch data for this transaction
            assertEquals(StoragePolicy.VIEWED, Settings.getInstance().getStoragePolicy());
            assertTrue(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));

            // Now unfollow the name
            assertTrue(ResourceListManager.getInstance().removeFromList("followed", "names", name, false));

            // We should store but not pre-fetch data for this transaction
            assertTrue(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));
        }
    }

    @Test
    public void testAll() throws DataException, IllegalAccessException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "Test";

            // Set the storage policy to "ALL"
            FieldUtils.writeField(Settings.getInstance(), "storagePolicy", "ALL", true);

            // Register the name to Alice
            TransactionUtils.signAndMint(repository, new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, ""), alice);

            // Create transaction
            ArbitraryTransactionData transactionData = this.createTxnWithName(repository, alice, name);

            // Add name to followed list
            assertTrue(ResourceListManager.getInstance().addToList("followed", "names", name, false));

            // We should store and pre-fetch data for this transaction
            assertEquals(StoragePolicy.ALL, Settings.getInstance().getStoragePolicy());
            assertTrue(storageManager.canStoreData(transactionData));
            assertTrue(storageManager.shouldPreFetchData(transactionData));

            // Now unfollow the name
            assertTrue(ResourceListManager.getInstance().removeFromList("followed", "names", name, false));

            // We should store and pre-fetch data for this transaction
            assertTrue(storageManager.canStoreData(transactionData));
            assertTrue(storageManager.shouldPreFetchData(transactionData));
        }
    }

    @Test
    public void testNone() throws DataException, IllegalAccessException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "Test";

            // Set the storage policy to "NONE"
            FieldUtils.writeField(Settings.getInstance(), "storagePolicy", "NONE", true);

            // Register the name to Alice
            TransactionUtils.signAndMint(repository, new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, ""), alice);

            // Create transaction
            ArbitraryTransactionData transactionData = this.createTxnWithName(repository, alice, name);

            // Add name to followed list
            assertTrue(ResourceListManager.getInstance().addToList("followed", "names", name, false));

            // We shouldn't store or pre-fetch data for this transaction
            assertEquals(StoragePolicy.NONE, Settings.getInstance().getStoragePolicy());
            assertFalse(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));

            // Now unfollow the name
            assertTrue(ResourceListManager.getInstance().removeFromList("followed", "names", name, false));

            // We shouldn't store or pre-fetch data for this transaction
            assertFalse(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));
        }
    }

    @Test
    public void testTransactionWithoutName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = null;

            // Create transaction
            ArbitraryTransactionData transactionData = this.createTxnWithName(repository, alice, name);

            // We should store but not pre-fetch data for this transaction
            assertTrue(storageManager.canStoreData(transactionData));
            assertFalse(storageManager.shouldPreFetchData(transactionData));
        }
    }

    private ArbitraryTransactionData createTxnWithName(Repository repository, PrivateKeyAccount acc, String name) throws DataException {
        String publicKey58 = Base58.encode(acc.getPublicKey());
        Path path = Paths.get("src/test/resources/arbitrary/demo1");

        ArbitraryDataTransactionBuilder txnBuilder = new ArbitraryDataTransactionBuilder(
                repository, publicKey58, path, name, Method.PUT, Service.WEBSITE, null);

        txnBuilder.build();
        ArbitraryTransactionData transactionData = txnBuilder.getArbitraryTransactionData();

        return transactionData;
    }

    private void deleteListsDirectory() {
        // Delete lists directory if exists
        Path listsPath = Paths.get(Settings.getInstance().getListsPath());
        try {
            FileUtils.deleteDirectory(listsPath.toFile());
        } catch (IOException e) {

        }
    }

}

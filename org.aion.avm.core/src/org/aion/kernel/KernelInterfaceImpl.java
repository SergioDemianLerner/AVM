package org.aion.kernel;

import java.math.BigInteger;
import org.aion.avm.core.util.Helpers;
import org.aion.data.DirectoryBackedDataStore;
import org.aion.data.IAccountStore;
import org.aion.data.IDataStore;
import org.aion.data.MemoryBackedDataStore;

import java.io.File;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.KernelInterface;


/**
 * Mostly just a high-level wrapper around and underlying IDataStore.
 * Note that this implementation implicitly creates accounts in response to mutative operations.  They are not explicitly created.
 * Likewise, reading data from a non-existent account safely returns null or 0L, rather than failing.
 */
public class KernelInterfaceImpl implements KernelInterface {

    public static final Address PREMINED_ADDRESS = AvmAddress.wrap(Helpers.hexStringToBytes("a025f4fd54064e869f158c1b4eb0ed34820f67e60ee80a53b469f725efc06378"));
    public static final BigInteger PREMINED_AMOUNT = BigInteger.TEN.pow(18);

    private final IDataStore dataStore;

    /**
     * Creates an instance of the interface which is backed by in-memory structures, only.
     */
    public KernelInterfaceImpl() {
        this.dataStore = new MemoryBackedDataStore();
        IAccountStore premined = this.dataStore.createAccount(PREMINED_ADDRESS.toBytes());
        premined.setBalance(PREMINED_AMOUNT);
    }

    /**
     * Creates an instance of the interface which is backed by a directory on disk.
     * 
     * @param onDiskRoot The root directory which this implementation will use for persistence.
     */
    public KernelInterfaceImpl(File onDiskRoot) {
        this.dataStore = new DirectoryBackedDataStore(onDiskRoot);
        // Try to open the account, creating it if doesn't exist.
        IAccountStore premined = this.dataStore.openAccount(PREMINED_ADDRESS.toBytes());
        if (null == premined) {
            premined = this.dataStore.createAccount(PREMINED_ADDRESS.toBytes());
        }
        premined.setBalance(PREMINED_AMOUNT);
    }

    @Override
    public KernelInterface makeChildKernelInterface() {
        return new TransactionalKernel(this);
    }

    @Override
    public void commit() {
        throw new AssertionError("This class does not implement this method.");
    }

    @Override
    public void commitTo(KernelInterface target) {
        throw new AssertionError("This class does not implement this method.");
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        throw new AssertionError("No equivalent concept in the Avm.");
    }

    @Override
    public void removeStorage(Address address, byte[] key) {
        throw new AssertionError("This class does not implement this method.");
    }

    @Override
    public void createAccount(Address address) {
        this.dataStore.createAccount(address.toBytes());
    }

    @Override
    public boolean hasAccountState(Address address) {
        return this.dataStore.openAccount(address.toBytes()) != null;
    }

    @Override
    public void putCode(Address address, byte[] code) {
        lazyCreateAccount(address.toBytes()).setCode(code);
    }

    @Override
    public byte[] getCode(Address address) {
        IAccountStore account = this.dataStore.openAccount(address.toBytes());
        return (null != account)
            ? account.getCode()
            : null;
    }

    @Override
    public void putStorage(Address address, byte[] key, byte[] value) {
        lazyCreateAccount(address.toBytes()).setData(key, value);
    }

    @Override
    public byte[] getStorage(Address address, byte[] key) {
        IAccountStore account = this.dataStore.openAccount(address.toBytes());
        return (null != account)
            ? account.getData(key)
            : null;
    }

    @Override
    public void deleteAccount(Address address) {
        this.dataStore.deleteAccount(address.toBytes());
    }

    @Override
    public BigInteger getBalance(Address address) {
        IAccountStore account = this.dataStore.openAccount(address.toBytes());
        return (null != account)
            ? account.getBalance()
            : BigInteger.ZERO;
    }

    @Override
    public void adjustBalance(Address address, BigInteger delta) {
        IAccountStore account = lazyCreateAccount(address.toBytes());
        BigInteger start = account.getBalance();
        account.setBalance(start.add(delta));
    }

    @Override
    public BigInteger getNonce(Address address) {
        IAccountStore account = this.dataStore.openAccount(address.toBytes());
        return (null != account)
            ? BigInteger.valueOf(account.getNonce())
            : BigInteger.ZERO;
    }

    @Override
    public void incrementNonce(Address address) {
        IAccountStore account = lazyCreateAccount(address.toBytes());
        long start = account.getNonce();
        account.setNonce(start + 1);
    }

    @Override
    public boolean accountNonceEquals(Address address, BigInteger nonce) {
        return nonce.compareTo(this.getNonce(address)) == 0;
    }

    @Override
    public boolean accountBalanceIsAtLeast(Address address, BigInteger amount) {
        return this.getBalance(address).compareTo(amount) >= 0;
    }

    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return energyLimit > 0;
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return energyLimit > 0;
    }

    private IAccountStore lazyCreateAccount(byte[] address) {
        IAccountStore account = this.dataStore.openAccount(address);
        if (null == account) {
            account = this.dataStore.createAccount(address);
        }
        return account;
    }

    @Override
    public boolean destinationAddressIsSafeForThisVM(Address address) {
        //TODO: implement this with logic that detects fvm addresses.
        return true;
    }

    @Override
    public void refundAccount(Address address, BigInteger amount) {
        // This method may have special logic in the kernel. Here it is just adjustBalance.
        adjustBalance(address, amount);
    }

    @Override
    public void deductEnergyCost(Address address, BigInteger cost) {
        // This method may have special logic in the kernel. Here it is just adjustBalance.
        adjustBalance(address, cost);
    }

    @Override
    public void payMiningFee(Address address, BigInteger fee) {
        // This method may have special logic in the kernel. Here it is just adjustBalance.
        adjustBalance(address, fee);
    }

}

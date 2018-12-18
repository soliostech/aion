package org.aion.mcf.vm.types;

import java.math.BigInteger;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.valid.TxNrgRule;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.KernelInterface;

public class KernelInterfaceForFastVM implements KernelInterface {
    private IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache;
    private boolean allowNonceIncrement, isLocalCall;

    public KernelInterfaceForFastVM(
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache,
            boolean allowNonceIncrement,
            boolean isLocalCall) {

        if (repositoryCache == null) {
            throw new NullPointerException("Cannot set null repositoryCache!");
        }
        this.repositoryCache = repositoryCache;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isLocalCall = isLocalCall;
    }

    // These 4 methods are temporary. Really any of this type of functionality should be moved out
    // into the kernel.
    @Override
    public KernelInterfaceForFastVM makeChildKernelInterface() {
        return new KernelInterfaceForFastVM(
                this.repositoryCache.startTracking(), this.allowNonceIncrement, this.isLocalCall);
    }

    @Override
    public void commit() {
        this.repositoryCache.flush();
    }

    @Override
    public void commitTo(KernelInterface kernel) {
        if (!(kernel instanceof KernelInterfaceForFastVM)) {
            throw new IllegalArgumentException("kernel must be of type KernelInterfaceForFastVM.");
        }
        KernelInterfaceForFastVM fvmKernel = (KernelInterfaceForFastVM) kernel;
        this.repositoryCache.flushTo(fvmKernel.repositoryCache, false);
    }

    public void rollback() {
        this.repositoryCache.rollback();
    }

    public IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> getRepositoryCache() {
        return this.repositoryCache;
    }
    // The above 4 methods are temporary. See comment just above.

    public void createAccount(Address address) {
        this.repositoryCache.createAccount(address);
    }

    @Override
    public void createAccount(byte[] address) {
        createAccount(AionAddress.wrap(address));
    }

    public boolean hasAccountState(Address address) {
        return this.repositoryCache.hasAccountState(address);
    }

    @Override
    public boolean hasAccountState(byte[] address) {
        return hasAccountState(AionAddress.wrap(address));
    }

    public void putCode(Address address, byte[] code) {
        this.repositoryCache.saveCode(address, code);
    }

    @Override
    public void putCode(byte[] address, byte[] code) {
        putCode(AionAddress.wrap(address), code);
    }

    public byte[] getCode(Address address) {
        return this.repositoryCache.getCode(address);
    }

    @Override
    public byte[] getCode(byte[] address) {
        return getCode(AionAddress.wrap(address));
    }

    public void putStorage(Address address, byte[] key, byte[] value) {
        ByteArrayWrapper storageKey = new DataWord(key).toWrapper();
        ByteArrayWrapper storageValue = new DataWord(value).toWrapper();
        this.repositoryCache.addStorageRow(address, storageKey, storageValue);
    }


    @Override
    public void putStorage(byte[] address, byte[] key, byte[] value) {
        putStorage(AionAddress.wrap(address), key, value);
    }

    public byte[] getStorage(Address address, byte[] key) {
        ByteArrayWrapper storageKey = new DataWord(key).toWrapper();
        ByteArrayWrapper value = this.repositoryCache.getStorageValue(address, storageKey);
        return (value == null) ? DataWord.ZERO.getData() : value.getData();
    }

    @Override
    public byte[] getStorage(byte[] address, byte[] key) {
        return getStorage(AionAddress.wrap(address), key);
    }

    public void deleteAccount(Address address) {
        if (!this.isLocalCall) {
            this.repositoryCache.deleteAccount(address);
        }
    }

    @Override
    public void deleteAccount(byte[] address) {
        deleteAccount(AionAddress.wrap(address));
    }

    public BigInteger getBalance(Address address) {
        return this.repositoryCache.getBalance(address);
    }

    @Override
    public BigInteger getBalance(byte[] address) {
        return getBalance(AionAddress.wrap(address));
    }

    public void adjustBalance(Address address, BigInteger delta) {
        this.repositoryCache.addBalance(address, delta);
    }

    @Override
    public void adjustBalance(byte[] address, BigInteger delta) {
        adjustBalance(AionAddress.wrap(address), delta);
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.repositoryCache.getBlockStore().getBlockHashByNumber(blockNumber);
    }

    public BigInteger getNonce(Address address) {
        return this.repositoryCache.getNonce(address);
    }

    @Override
    public BigInteger getNonce(byte[] address) {
        return getNonce(AionAddress.wrap(address));
    }

    public void incrementNonce(Address address) {
        if (!this.isLocalCall && this.allowNonceIncrement) {
            this.repositoryCache.incrementNonce(address);
        }
    }

    @Override
    public void incrementNonce(byte[] address) {
        incrementNonce(AionAddress.wrap(address));
    }

    public void deductEnergyCost(Address address, BigInteger energyCost) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, energyCost.negate());
        }
    }

    @Override
    public void deductEnergyCost(byte[] address, BigInteger energyCost) {
        deductEnergyCost(AionAddress.wrap(address), energyCost);
    }

    public void refundAccount(Address address, BigInteger amount) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, amount);
        }
    }

    @Override
    public void refundAccount(byte[] address, BigInteger amount) {
        refundAccount(AionAddress.wrap(address), amount);
    }

    public void payMiningFee(Address miner, BigInteger fee) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(miner, fee);
        }
    }

    @Override
    public void payMiningFee(byte[] miner, BigInteger fee) {
        payMiningFee(AionAddress.wrap(miner), fee);
    }

    public boolean accountNonceEquals(Address address, BigInteger nonce) {
        return (this.isLocalCall) ? true : getNonce(address).equals(nonce);
    }

    @Override
    public boolean accountNonceEquals(byte[] address, BigInteger nonce) {
        return accountNonceEquals(AionAddress.wrap(address), nonce);
    }

    public boolean accountBalanceIsAtLeast(Address address, BigInteger amount) {
        return (this.isLocalCall) ? true : getBalance(address).compareTo(amount) >= 0;
    }

    @Override
    public boolean accountBalanceIsAtLeast(byte[] address, BigInteger amount) {
        return accountBalanceIsAtLeast(AionAddress.wrap(address), amount);
    }

    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgContractCreate(energyLimit);
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgTx(energyLimit);
    }

    public boolean destinationAddressIsSafeForThisVM(Address address) {
        //TODO: replace with actual logic that prevents the FastVM from calling an Avm contract.
        return true;
    }

    @Override
    public boolean destinationAddressIsSafeForThisVM(byte[] address) {
        return destinationAddressIsSafeForThisVM(AionAddress.wrap(address));
    }

}

/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
package org.aion.mcf.account;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/** Account Manger Class */
// TODO: there should be a better way to abstract account creation
public class AccountManager {

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.API.name());
    private static final int ACC_SIZE = 10;

    private Map<Address, Account> accounts;

    private AccountManager() {
        LOGGER.debug("<account-manager init>");
        accounts = new HashMap<>();

        for (int i = 0; i < ACC_SIZE; i++) {
            // generate account and display
            ECKey key = ECKeyFac.inst().create();
            accounts.put(new Address(key.getAddress()), new Account(key, 0));
        }
    }

    private static class Holder {
        static final AccountManager INSTANCE = new AccountManager();
    }

    public static AccountManager inst() {
        return Holder.INSTANCE;
    }

    // Retrieve ECKey from active accounts list from manager perspective
    // !important method. use in careful
    // Can use this method as check if unlocked
    public ECKey getKey(final Address _address) {

        Account acc = this.accounts.get(_address);

        if (Optional.ofNullable(acc).isPresent()) {
            if (acc.getTimeout() >= Instant.now().getEpochSecond()) {
                return acc.getKey();
            } else {
                this.accounts.remove(_address);
            }
        }
        return null;
    }

    public List<Account> getAccounts() {
        return new ArrayList<>(this.accounts.values());
    }

    public boolean unlockAccount(Address _address, String _password, int _timeout) {
        // do nothing, since everything is preloaded
        return this.accounts.containsKey(_address);
    }

    public boolean lockAccount(Address _address, String _password) {
        // just assume that the unlock was successful
        return this.accounts.containsKey(_address);
    }
}

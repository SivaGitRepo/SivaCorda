package net.corda.samples.auction.contracts;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

public class IouContract implements Contract {
    public static final String ID = "net.corda.samples.auction.contracts.IouContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) {
        return;
        //requireSingleCommand(tx.getCommands()
    }
    public interface Commands extends CommandData {
        class Create implements Commands {}
    }
}

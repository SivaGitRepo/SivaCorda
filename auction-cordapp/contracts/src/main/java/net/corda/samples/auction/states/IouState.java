package net.corda.samples.auction.states;

import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.samples.auction.contracts.IouContract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@BelongsToContract(IouContract.class)
public class IouState implements ContractState
{

    private final int value;
    private final Party lender;
    private final Party borrower;

    public IouState(int value, Party lender, Party borrower) {
        this.value = value;
        this.lender = lender;
        this.borrower = borrower;
    }

    public int getValue() {
        return value;
    }

    public Party getLender() {
        return lender;
    }

    public Party getBorrower() {
        return borrower;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(lender,borrower);
    }
}
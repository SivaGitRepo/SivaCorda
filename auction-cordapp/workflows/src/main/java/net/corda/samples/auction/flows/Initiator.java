package net.corda.samples.auction.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.samples.auction.contracts.IouContract;
import net.corda.samples.auction.states.IouState;

@InitiatingFlow
@StartableByRPC
public class Initiator extends FlowLogic<Void> {

    private final int iouValue;
    private final Party otherParty;

    private final ProgressTracker progressTracker = new ProgressTracker();

    public Initiator(int iouValue, Party otherParty) {
        this.iouValue = iouValue;
        this.otherParty = otherParty;
    }

    public final ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    public Void call() throws FlowException {
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final IouState output = new IouState(iouValue, getOurIdentity(), otherParty);

        System.out.println("----------------inside initiator --------------------------------------------------");
        System.out.println(iouValue);
        System.out.println(getOurIdentity());
        System.out.println(otherParty);

        Command command = new Command<>(new IouContract.Commands.Create(), getOurIdentity().getOwningKey());

        final TransactionBuilder builder = new TransactionBuilder(notary);
        builder.addOutputState(output);
        builder.addCommand(command);

        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(builder);

        FlowSession otherPartySession = initiateFlow(otherParty);
        subFlow(new FinalityFlow(signedTx, otherPartySession));
        //subFlow(new FinalityFlow(signedTx));

        return null;
    }
}

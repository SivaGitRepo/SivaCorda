package net.corda.samples.auction.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;

@InitiatedBy(Initiator.class)
public class Responder extends FlowLogic<Void> {

    private FlowSession otherPartySession;

    public Responder(FlowSession otherPartySession) { this.otherPartySession = otherPartySession; }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        System.out.println("----------------inside responder --------------------------------------------------");
        System.out.println(otherPartySession);

        subFlow(new ReceiveFinalityFlow(otherPartySession));
        return null;
    }
}

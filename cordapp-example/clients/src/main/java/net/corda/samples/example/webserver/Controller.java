package net.corda.samples.example.webserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.transactions.SignedTransaction;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.contracts.asset.Cash;
import net.corda.finance.flows.CashIssueAndPaymentFlow;
import net.corda.samples.example.flows.ExampleFlow;
import net.corda.samples.example.states.IOUState;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Min;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
public class Controller {

    @Autowired
    private CordaRPCOps partyAProxy;
    @Autowired
    private CordaRPCOps partyCProxy;
    @Autowired
    private CordaRPCOps partyBProxy;
    @Autowired
    @Qualifier("partyAProxy")
    private CordaRPCOps activeParty;

    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private CordaRPCOps proxy;
    private CordaX500Name me;

    public Controller(NodeRPCConnection rpc) {
        this.proxy = rpc.proxy;
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();
    }
    /** Helpers for filtering the network map cache. */
    public String toDisplayString(X500Name name){
        return BCStyle.INSTANCE.toString(name);
    }

    private boolean isNotary(NodeInfo nodeInfo) {
        return !proxy.notaryIdentities()
                .stream().filter(el -> nodeInfo.isLegalIdentity(el))
                .collect(Collectors.toList()).isEmpty();
    }

    private boolean isMe(NodeInfo nodeInfo){
        return nodeInfo.getLegalIdentities().get(0).getName().equals(me);
    }

    private boolean isNetworkMap(NodeInfo nodeInfo){
        return nodeInfo.getLegalIdentities().get(0).getName().getOrganisation().equals("Network Map Service");
    }

    @Configuration
    class Plugin {
        @Bean
        public ObjectMapper registerModule() {
            return JacksonSupport.createNonRpcMapper();
        }
    }

    @GetMapping(value = "/status", produces = TEXT_PLAIN_VALUE)
    private String status() {
        return "200";
    }

    @GetMapping(value = "/servertime", produces = TEXT_PLAIN_VALUE)
    private String serverTime() {
        return (LocalDateTime.ofInstant(proxy.currentNodeTime(), ZoneId.of("UTC"))).toString();
    }

    @GetMapping(value = "/addresses", produces = TEXT_PLAIN_VALUE)
    private String addresses() {
        return proxy.nodeInfo().getAddresses().toString();
    }

    @GetMapping(value = "/identities", produces = TEXT_PLAIN_VALUE)
    private String identities() {
        return proxy.nodeInfo().getLegalIdentities().toString();
    }

    @GetMapping(value = "/platformversion", produces = TEXT_PLAIN_VALUE)
    private String platformVersion() {
        return Integer.toString(proxy.nodeInfo().getPlatformVersion());
    }

    @GetMapping(value = "/peers", produces = APPLICATION_JSON_VALUE)
    public HashMap<String, List<String>> getPeers() {
        HashMap<String, List<String>> myMap = new HashMap<>();

        // Find all nodes that are not notaries, ourself, or the network map.
        Stream<NodeInfo> filteredNodes = proxy.networkMapSnapshot().stream()
                .filter(el -> !isNotary(el) && !isMe(el) && !isNetworkMap(el));
        // Get their names as strings
        List<String> nodeNames = filteredNodes.map(el -> el.getLegalIdentities().get(0).getName().toString())
                .collect(Collectors.toList());

        myMap.put("peers", nodeNames);
        return myMap;
    }

    @GetMapping(value = "/notaries", produces = TEXT_PLAIN_VALUE)
    private String notaries() {
        return proxy.notaryIdentities().toString();
    }

    @GetMapping(value = "/flows", produces = TEXT_PLAIN_VALUE)
    private String flows() {
        return proxy.registeredFlows().toString();
    }

    @GetMapping(value = "/states", produces = TEXT_PLAIN_VALUE)
    private String states() {
        return proxy.vaultQuery(ContractState.class).getStates().toString();
    }

    @GetMapping(value = "/me",produces = APPLICATION_JSON_VALUE)
    private HashMap<String, String> whoami(){
        HashMap<String, String> myMap = new HashMap<>();
        myMap.put("me", me.toString());
        return myMap;
    }
    @GetMapping(value = "/ious",produces = APPLICATION_JSON_VALUE)
    public List<StateAndRef<IOUState>> getIOUs() {
        // Filter by state type: IOU.
        return proxy.vaultQuery(IOUState.class).getStates();
    }

    @PostMapping (value = "issueCash" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> issueCash(HttpServletRequest request) throws IllegalArgumentException {

        int amount = Integer. valueOf(request.getParameter("iouValue"));
        String partyName = request.getParameter("partyName");

        if(partyName.contains("PartyB")){
            proxy = partyBProxy;
        }else if(partyName.contains("PartyA")){
            proxy = partyAProxy;
        }else if(partyName.contains("PartyC")){
            proxy = partyCProxy;
        }

        CordaX500Name partyX500Name = CordaX500Name.parse(partyName);
        Party party = proxy.wellKnownPartyFromX500Name(partyX500Name);

        try {
            proxy.startFlowDynamic(CashIssueAndPaymentFlow.class,
                            Amount.parseCurrency(amount + " USD"),
                            //OpaqueBytes.of("PartyA".getBytes()),
                            OpaqueBytes.of(partyName.getBytes()),
                            party,
                            //proxy.partiesFromName(partyName, false).iterator().next(),
                            false,
                            proxy.notaryIdentities().get(0))
                    .getReturnValue().get();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id committed to ledger");
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Error :::: Transaction id not committed to ledger" + e);
                    //.body(e.getMessage());
        }
    }

    @GetMapping (value = "/cashBalance" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    private String cashBalance(HttpServletRequest request) throws IllegalArgumentException {

        String partyName = request.getParameter("partyName");

        if(partyName.contains("PartyB")){
            proxy = partyBProxy;
        }else if(partyName.contains("PartyA")){
            proxy = partyAProxy;
        }else if(partyName.contains("PartyC")){
            proxy = partyCProxy;
        }

        CordaX500Name partyX500Name = CordaX500Name.parse(partyName);
        Party party = proxy.wellKnownPartyFromX500Name(partyX500Name);

        //return proxy.vaultQuery(Cash.State.class).getStates().toString();
        List<StateAndRef<Cash.State>> cashStateList = proxy.vaultQuery(Cash.State.class).getStates();
        Long amount = 0L;
        if(cashStateList.size()>0) {
            amount = cashStateList.stream().map(stateStateAndRef ->
                    stateStateAndRef.getState().getData().getAmount().getQuantity()).reduce(Long::sum).get();
            if (amount >= 100) {
                amount = amount / 100;
            } else {
                amount = 0L;
            }
        }
        return amount.toString();
    }
    @PostMapping (value = "create-iou" , produces =  TEXT_PLAIN_VALUE , headers =  "Content-Type=application/x-www-form-urlencoded" )
    public ResponseEntity<String> issueIOU(HttpServletRequest request) throws IllegalArgumentException {

        int amount = Integer. valueOf(request.getParameter("iouValue"));
        String party = request.getParameter("partyName");
        // Get party objects for myself and the counterparty.

        CordaX500Name partyX500Name = CordaX500Name.parse(party);
        Party otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name);

        // Create a new IOU state using the parameters given.
        try {
            // Start the IOUIssueFlow. We block and waits for the flow to return.
            SignedTransaction result = proxy.startTrackedFlowDynamic(ExampleFlow.Initiator.class, amount,otherParty).getReturnValue().get();
            // Return the response.
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Transaction id "+ result.getId() +" committed to ledger.\n " + result.getTx().getOutput(0));
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
    /**
     * Displays all IOU states that only this node has been involved in.
     */
    @GetMapping(value = "my-ious",produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StateAndRef<IOUState>>> getMyIOUs() {
        List<StateAndRef<IOUState>> myious = proxy.vaultQuery(IOUState.class).getStates().stream().filter(
                it -> it.getState().getData().getLender().equals(proxy.nodeInfo().getLegalIdentities().get(0))).collect(Collectors.toList());
        return ResponseEntity.ok(myious);
    }
}
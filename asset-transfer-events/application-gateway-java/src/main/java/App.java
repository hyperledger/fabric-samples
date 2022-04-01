/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.grpc.ManagedChannel;
import org.hyperledger.fabric.client.CallOption;
import org.hyperledger.fabric.client.ChaincodeEvent;
import org.hyperledger.fabric.client.ChaincodeEventsRequest;
import org.hyperledger.fabric.client.CloseableIterator;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.Status;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.SubmittedTransaction;

public final class App {
	private static final String channelName = "mychannel";
	private static final String chaincodeName = "events";

	private final Network network;
	private final Contract contract;
	private final String assetId = "asset" + Instant.now().toEpochMilli();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static void main(final String[] args) throws Exception {
		ManagedChannel grpcChannel = Connections.newGrpcConnection();
		Gateway.Builder builder = Gateway.newInstance()
				.identity(Connections.newIdentity())
				.signer(Connections.newSigner())
				.connection(grpcChannel)
				.evaluateOptions(CallOption.deadlineAfter(5, TimeUnit.SECONDS))
				.endorseOptions(CallOption.deadlineAfter(15, TimeUnit.SECONDS))
				.submitOptions(CallOption.deadlineAfter(5, TimeUnit.SECONDS))
				.commitStatusOptions(CallOption.deadlineAfter(1, TimeUnit.MINUTES));

		try (Gateway gateway = builder.connect()) {
			new App(gateway).run();
		} finally {
			grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	public App(final Gateway gateway) {
		network = gateway.getNetwork(channelName);
		contract = network.getContract(chaincodeName);
	}

	public void run() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		// Listen for events emitted by subsequent transactions, stopping when the try-with-resources block exits
		try (CloseableIterator<ChaincodeEvent> eventSession = startChaincodeEventListening()) {
			long firstBlockNumber = createAsset();
			updateAsset();
			transferAsset();
			deleteAsset();

			// Replay events from the block containing the first transaction
			replayChaincodeEvents(firstBlockNumber);
		}
	}

	private CloseableIterator<ChaincodeEvent> startChaincodeEventListening() {
		System.out.println("\n*** Start chaincode event listening");

		CloseableIterator<ChaincodeEvent> eventIter = network.getChaincodeEvents(chaincodeName);

		CompletableFuture.runAsync(() -> {
			eventIter.forEachRemaining(event -> {
				String payload = prettyJson(event.getPayload());
				System.out.println("\n<-- Chaincode event received: " + event.getEventName() + " - " + payload);
			});
		});

		return eventIter;
	}

	private String prettyJson(final byte[] json) {
		return prettyJson(new String(json, StandardCharsets.UTF_8));
	}

	private String prettyJson(final String json) {
		JsonElement parsedJson = JsonParser.parseString(json);
		return gson.toJson(parsedJson);
	}

	private long createAsset() throws EndorseException, SubmitException, CommitStatusException {
		System.out.println("\n--> Submit transaction: CreateAsset, " + assetId + " owned by Sam with appraised value 100");

		SubmittedTransaction commit = contract.newProposal("CreateAsset")
				.addArguments(assetId, "blue", "10", "Sam", "100")
				.build()
				.endorse()
				.submitAsync();

		Status status = commit.getStatus();
		if (!status.isSuccessful()) {
			throw new RuntimeException("failed to commit transaction with status code " + status.getCode());
		}

		System.out.println("\n*** CreateAsset committed successfully");

		return status.getBlockNumber();
	}

	private void updateAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit transaction: UpdateAsset, " + assetId + " update appraised value to 200");

		contract.submitTransaction("UpdateAsset", assetId, "blue", "10", "Sam", "200");

		System.out.println("\n*** UpdateAsset committed successfully");
	}

	private void transferAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit transaction: TransferAsset, " + assetId + " to Mary");

		contract.submitTransaction("TransferAsset", assetId, "Mary");

		System.out.println("\n*** TransferAsset committed successfully");
	}

	private void deleteAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit transaction: DeleteAsset, " + assetId);

		contract.submitTransaction("DeleteAsset", assetId);

		System.out.println("\n*** DeleteAsset committed successfully");
	}

	private void replayChaincodeEvents(final long startBlock) {
		System.out.println("\n*** Start chaincode event replay");

		ChaincodeEventsRequest request = network.newChaincodeEventsRequest(chaincodeName)
				.startBlock(startBlock)
				.build();

		try (CloseableIterator<ChaincodeEvent> eventIter = request.getEvents()) {
			while (eventIter.hasNext()) {
				ChaincodeEvent event = eventIter.next();
				String payload = prettyJson(event.getPayload());
				System.out.println("\n<-- Chaincode event replayed: " + event.getEventName() + " - " + payload);

				if (event.getEventName().equals("DeleteAsset")) {
					// Reached the last submitted transaction so break to close the iterator and stop listening for events
					break;
				}
			}
		}
	}
}

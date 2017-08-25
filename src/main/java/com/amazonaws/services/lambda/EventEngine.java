package com.amazonaws.services.lambda;

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Future;

import org.hjson.JsonArray;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import org.hjson.Stringify;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EventEngine {
	private final String eventGraphLocation;
	private JsonObject executionConfig;
	private AWSLambda lambdaSyncClient;
	private AWSLambdaAsync lambdaAsyncClient;
	private final String INVOKE_SYNC = "sync";

	/*
	 * turn the local list of args from the event engine definition into a Json
	 * object serialised to a byte[]
	 */
	private final ByteBuffer extractArgs(Map<String, String> args, JsonArray argArray) {
		JsonObject toSerialise = new JsonObject();

		for (JsonValue v : argArray) {
			String t = v.asString();
			if (args.containsKey(t)) {
				toSerialise.add(t, args.get(t));
			}
		}

		return ByteBuffer.wrap(toSerialise.toString(Stringify.PLAIN).getBytes());
	}

	/* Lambda function invoker */
	private final Void invokeFunction(Map<String, String> args, JsonObject f) throws Exception {
		String functionLabel = f.names().get(0);
		JsonObject function = f.get(functionLabel).asObject();
		String[] arnTokens = function.get("arn").asString().split(":");
		String functionName = arnTokens[6];
		String functionVersion = arnTokens.length == 8 ? arnTokens[7] : null;
		String fqfn = functionVersion == null ? functionName : functionName + ":" + functionVersion;

		System.out.println(String.format("Invoking function %s @ %s:%s", functionLabel,
				function.getString("arn", "Unknown"), function.get("invoke").asString()));

		InvokeRequest req = new InvokeRequest().withFunctionName(fqfn);

		// add arguments required by the execution graph
		if (function.get("args") != null) {
			req.withPayload(extractArgs(args, function.get("args").asArray()));
		}

		// call the sync or async versions of the lambda client with the request
		InvokeResult invokeResult = null;
		if (function.getString("invoke", INVOKE_SYNC).equals(INVOKE_SYNC)) {
			invokeResult = lambdaSyncClient.invoke(req);
		} else {
			Future<InvokeResult> future = lambdaAsyncClient.invokeAsync(req);
			// rubbish blocking implementation to demonstrate
			while (!future.isDone()) {
				Thread.sleep(100);
			}
			invokeResult = future.get();
		}

		// spew errors or results
		if (invokeResult.getFunctionError() != null) {
			// throw an exception - but here you could instead do things like
			// push the message onto a DLQ and confirm OK, etc
			throw new Exception(invokeResult.getFunctionError());
		} else {
			System.out.println(new String(invokeResult.getPayload().array()));
		}

		// recurse into self for child functions - note this of course doesn't
		// implement tail call optimisation so don't actually use this ever
		if (function.get("children") != null) {
			for (JsonValue j : function.get("children").asArray()) {
				JsonObject child = j.asObject();
				invokeFunction(args, child);
			}
		}

		return null;
	}

	// method to execute an EventEngine with a supplied set of arguments
	public final Void doExecute(Map<String, String> args) throws Exception {
		if (executionConfig == null) {
			URI configURI = getClass().getResource(eventGraphLocation).toURI();
			Path p = Paths.get(configURI);

			if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
				executionConfig = JsonValue.readHjson(new InputStreamReader(Files.newInputStream(p))).asObject();
			} else {
				throw new Exception(String.format("Unable to find configuration at %s", eventGraphLocation));
			}

			Regions r = Regions.fromName(executionConfig.getString("region", "us-east-1"));
			lambdaSyncClient = AWSLambdaClientBuilder.standard().withRegion(r).build();
			lambdaAsyncClient = AWSLambdaAsyncClientBuilder.standard().withRegion(r).build();
		}

		System.out.println(String.format("Starting Execution of Event Engine Graph \"%s\" version %s",
				executionConfig.getString("name", "unknown"),
				executionConfig.getString("version", "no version information")));

		for (JsonValue f : executionConfig.get("functionOutline").asArray()) {
			// run each function in the function outline
			JsonObject function = f.asObject();
			invokeFunction(args, function);
		}
		return null;
	}
}

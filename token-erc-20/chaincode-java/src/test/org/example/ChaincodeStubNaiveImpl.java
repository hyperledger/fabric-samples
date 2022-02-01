package org.example;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hyperledger.fabric.protos.msp.Identities.SerializedIdentity;
import org.hyperledger.fabric.protos.peer.ChaincodeEventPackage;
import org.hyperledger.fabric.protos.peer.ProposalPackage;
import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;

import com.google.protobuf.ByteString;
import static java.nio.charset.StandardCharsets.UTF_8;
public final class ChaincodeStubNaiveImpl implements ChaincodeStub {
    private List<String> args;
    private List<byte[]> argsAsByte;
    private final Map<String, ByteString> state;
    private final Chaincode.Response resp;
   
    public  String certificate = "MIICXTCCAgSgAwIBAgIUeLy6uQnq8wwyElU/jCKRYz3tJiQwCgYIKoZIzj0EAwIw"
            + "eTELMAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcTDVNh" + "biBGcmFuY2lzY28xGTAXBgNVBAoTEEludGVybmV0IFdpZGdldHMxDDAKBgNVBAsT"
            + "A1dXVzEUMBIGA1UEAxMLZXhhbXBsZS5jb20wHhcNMTcwOTA4MDAxNTAwWhcNMTgw" + "OTA4MDAxNTAwWjBdMQswCQYDVQQGEwJVUzEXMBUGA1UECBMOTm9ydGggQ2Fyb2xp"
            + "bmExFDASBgNVBAoTC0h5cGVybGVkZ2VyMQ8wDQYDVQQLEwZGYWJyaWMxDjAMBgNV" + "BAMTBWFkbWluMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFq/90YMuH4tWugHa"
            + "oyZtt4Mbwgv6CkBSDfYulVO1CVInw1i/k16DocQ/KSDTeTfgJxrX1Ree1tjpaodG" + "1wWyM6OBhTCBgjAOBgNVHQ8BAf8EBAMCB4AwDAYDVR0TAQH/BAIwADAdBgNVHQ4E"
            + "FgQUhKs/VJ9IWJd+wer6sgsgtZmxZNwwHwYDVR0jBBgwFoAUIUd4i/sLTwYWvpVr" + "TApzcT8zv/kwIgYDVR0RBBswGYIXQW5pbHMtTWFjQm9vay1Qcm8ubG9jYWwwCgYI"
            + "KoZIzj0EAwIDRwAwRAIgCoXaCdU8ZiRKkai0QiXJM/GL5fysLnmG2oZ6XOIdwtsC" + "IEmCsI8Mhrvx1doTbEOm7kmIrhQwUVDBNXCWX1t3kJVN";

    public static final String CERT_WITH_ATTRS = "MIIB6TCCAY+gAwIBAgIUHkmY6fRP0ANTvzaBwKCkMZZPUnUwCgYIKoZIzj0EAwIw"
            + "GzEZMBcGA1UEAxMQZmFicmljLWNhLXNlcnZlcjAeFw0xNzA5MDgwMzQyMDBaFw0x" + "ODA5MDgwMzQyMDBaMB4xHDAaBgNVBAMTE015VGVzdFVzZXJXaXRoQXR0cnMwWTAT"
            + "BgcqhkjOPQIBBggqhkjOPQMBBwNCAATmB1r3CdWvOOP3opB3DjJnW3CnN8q1ydiR" + "dzmuA6A2rXKzPIltHvYbbSqISZJubsy8gVL6GYgYXNdu69RzzFF5o4GtMIGqMA4G"
            + "A1UdDwEB/wQEAwICBDAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBTYKLTAvJJK08OM" + "VGwIhjMQpo2DrjAfBgNVHSMEGDAWgBTEs/52DeLePPx1+65VhgTwu3/2ATAiBgNV"
            + "HREEGzAZghdBbmlscy1NYWNCb29rLVByby5sb2NhbDAmBggqAwQFBgcIAQQaeyJh" + "dHRycyI6eyJhdHRyMSI6InZhbDEifX0wCgYIKoZIzj0EAwIDSAAwRQIhAPuEqWUp"
            + "svTTvBqLR5JeQSctJuz3zaqGRqSs2iW+QB3FAiAIP0mGWKcgSGRMMBvaqaLytBYo" + "9v3hRt1r8j8vN0pMcg==";

    public static final String CERT_WITH_DNS = "MIICGjCCAcCgAwIBAgIRAIPRwJHVLhHK47XK0BbFZJswCgYIKoZIzj0EAwIwczEL"
            + "MAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcTDVNhbiBG" + "cmFuY2lzY28xGTAXBgNVBAoTEG9yZzIuZXhhbXBsZS5jb20xHDAaBgNVBAMTE2Nh"
            + "Lm9yZzIuZXhhbXBsZS5jb20wHhcNMTcwNjIzMTIzMzE5WhcNMjcwNjIxMTIzMzE5" + "WjBbMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMN"
            + "U2FuIEZyYW5jaXNjbzEfMB0GA1UEAwwWVXNlcjFAb3JnMi5leGFtcGxlLmNvbTBZ" + "MBMGByqGSM49AgEGCCqGSM49AwEHA0IABBd9SsEiFH1/JIb3qMEPLR2dygokFVKW"
            + "eINcB0Ni4TBRkfIWWUJeCANTUY11Pm/+5gs+fBTqBz8M2UzpJDVX7+2jTTBLMA4G" + "A1UdDwEB/wQEAwIHgDAMBgNVHRMBAf8EAjAAMCsGA1UdIwQkMCKAIKfUfvpGproH"
            + "cwyFD+0sE3XfJzYNcif0jNwvgOUFZ4AFMAoGCCqGSM49BAMCA0gAMEUCIQC8NIMw" + "e4ym/QRwCJb5umbONNLSVQuEpnPsJrM/ssBPvgIgQpe2oYa3yO3USro9nBHjpM3L"
            + "KsFQrpVnF8O6hoHOYZQ=";

    public static final String CERT_MULTIPLE_ATTRIBUTES = "MIIChzCCAi6gAwIBAgIURilAHeqwLu/fNUv8eZoGPRh3H4IwCgYIKoZIzj0EAwIw"
            + "czELMAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcTDVNh" + "biBGcmFuY2lzY28xGTAXBgNVBAoTEG9yZzEuZXhhbXBsZS5jb20xHDAaBgNVBAMT"
            + "E2NhLm9yZzEuZXhhbXBsZS5jb20wHhcNMTkwNzMxMTYxNzAwWhcNMjAwNzMwMTYy" + "MjAwWjAgMQ8wDQYDVQQLEwZjbGllbnQxDTALBgNVBAMTBHRlc3QwWTATBgcqhkjO"
            + "PQIBBggqhkjOPQMBBwNCAAR2taQK8w7D3hr3gBxCz+8eV4KSv7pFQfNjDHMMe9J9" + "LJwcLpVTT5hYiLLRaqQonLBxBE3Ey0FneySvFuBScas3o4HyMIHvMA4GA1UdDwEB"
            + "/wQEAwIHgDAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQi3mhXS/WzcjBniwAmPdYP" + "kHqVVzArBgNVHSMEJDAigCC7VXjmSEugjAB/A0S6vfMxLsUIgag9WVNwtwwebnRC"
            + "7TCBggYIKgMEBQYHCAEEdnsiYXR0cnMiOnsiYXR0cjEiOiJ2YWwxIiwiZm9vIjoi" + "YmFyIiwiaGVsbG8iOiJ3b3JsZCIsImhmLkFmZmlsaWF0aW9uIjoiIiwiaGYuRW5y"
            + "b2xsbWVudElEIjoidGVzdCIsImhmLlR5cGUiOiJjbGllbnQifX0wCgYIKoZIzj0E" + "AwIDRwAwRAIgQxEFvnZTEsf3CSZmp9IYsxcnEOtVYleOd86LAKtk1wICIH7XOPwW"
            + "/RE4Z8WLZzFei/78Oezbx6obOvBxPMsVWRe5";

    public ChaincodeStubNaiveImpl() {

        args = new ArrayList<>();
        args.add("func1");
        args.add("param1");
        args.add("param2");
        state = new HashMap<>();
        state.put("a", ByteString.copyFrom("asdf", StandardCharsets.UTF_8));
        argsAsByte = null;
        resp = new Chaincode.Response(404, "Wrong cc name", new byte[] {});

    }

    ChaincodeStubNaiveImpl(final List<String> args) {
        this.args = args;
        state = new HashMap<>();
        state.put("a", ByteString.copyFrom("asdf", StandardCharsets.UTF_8));

        argsAsByte = null;

        resp = new Chaincode.Response(404, "Wrong cc name", new byte[] {});
    }

    @Override
    public List<byte[]> getArgs() {
        if (argsAsByte == null) {
            argsAsByte = args.stream().map(i -> i.getBytes()).collect(Collectors.toList());
        }
        return argsAsByte;
    }

    @Override
    public List<String> getStringArgs() {
        return args;
    }

    @Override
    public String getFunction() {
        return args.get(0);
    }

    @Override
    public List<String> getParameters() {
        return args.subList(1, args.size());
    }

    @Override
    public String getTxId() {
        return "tx0";
    }

    @Override
    public String getChannelId() {
        return "ch0";
    }

    @Override
    public Chaincode.Response invokeChaincode(final String chaincodeName, final List<byte[]> args, final String channel) {
        return resp;


    }
    
    public void putStringState(final String key, final String value) {
        putState(key, value.getBytes(UTF_8));
    }
    

    public String getStringState(final String key) {
        //return new String(getState(key), UTF_8);
        if(state.get(key) == null)
        return "";
        else
        return new String(getState(key), UTF_8);
    }

    @Override
    public byte[] getState(final String key) {
        return state.get(key).toByteArray();
    }

    @Override
    public byte[] getStateValidationParameter(final String key) {
        return new byte[0];
    }

    @Override
    public void putState(final String key, final byte[] value) {
        state.put(key, ByteString.copyFrom(value));

    }

    @Override
    public void setStateValidationParameter(final String key, final byte[] value) {

    }

    @Override
    public void delState(final String key) {
        state.remove(key);
    }

    @Override
    public QueryResultsIterator<KeyValue> getStateByRange(final String startKey, final String endKey) {
        return null;
    }

    @Override
    public QueryResultsIteratorWithMetadata<KeyValue> getStateByRangeWithPagination(final String startKey, final String endKey, final int pageSize,
            final String bookmark) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(final String compositeKey) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(final String objectType, final String... attributes) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(final CompositeKey compositeKey) {
        return null;
    }

    @Override
    public QueryResultsIteratorWithMetadata<KeyValue> getStateByPartialCompositeKeyWithPagination(final CompositeKey compositeKey, final int pageSize,
            final String bookmark) {
        return null;
    }

    @Override
    public CompositeKey createCompositeKey(final String objectType, final String... attributes) {
        final CompositeKey key = new CompositeKey(objectType, attributes);
        return key;
    }

    @Override
    public CompositeKey splitCompositeKey(final String compositeKey) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getQueryResult(final String query) {
        return null;
    }

    @Override
    public QueryResultsIteratorWithMetadata<KeyValue> getQueryResultWithPagination(final String query, final int pageSize, final String bookmark) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyModification> getHistoryForKey(final String key) {
        return null;
    }

    @Override
    public byte[] getPrivateData(final String collection, final String key) {
        return new byte[0];
    }

    @Override
    public byte[] getPrivateDataHash(final String collection, final String key) {
        return new byte[0];
    }

    @Override
    public byte[] getPrivateDataValidationParameter(final String collection, final String key) {
        return new byte[0];
    }

    @Override
    public void putPrivateData(final String collection, final String key, final byte[] value) {

    }

    @Override
    public void setPrivateDataValidationParameter(final String collection, final String key, final byte[] value) {

    }

    @Override
    public void delPrivateData(final String collection, final String key) {

    }

    @Override
    public QueryResultsIterator<KeyValue> getPrivateDataByRange(final String collection, final String startKey, final String endKey) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(final String collection, final String compositeKey) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(final String collection, final CompositeKey compositeKey) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(final String collection, final String objectType, final String... attributes) {
        return null;
    }

    @Override
    public QueryResultsIterator<KeyValue> getPrivateDataQueryResult(final String collection, final String query) {
        return null;
    }

    @Override
    public void setEvent(final String name, final byte[] payload) {

    }

    @Override
    public ChaincodeEventPackage.ChaincodeEvent getEvent() {
        return null;
    }

    @Override
    public ProposalPackage.SignedProposal getSignedProposal() {
        return null;
    }

    @Override
    public Instant getTxTimestamp() {
        return null;
    }

    @Override
    public byte[] getCreator() {
        return buildSerializedIdentity();
    }

    @Override
    public Map<String, byte[]> getTransient() {
        return null;
    }

    @Override
    public byte[] getBinding() {
        return new byte[0];
    }

    void setStringArgs(final List<String> args) {
        this.args = args;
        this.argsAsByte = args.stream().map(i -> i.getBytes()).collect(Collectors.toList());
    }

    public byte[] buildSerializedIdentity() {
        final SerializedIdentity.Builder identity = SerializedIdentity.newBuilder();
        identity.setMspid("Org1MSP");
        final byte[] decodedCert = Base64.getDecoder().decode(this.certificate);
        identity.setIdBytes(ByteString.copyFrom(decodedCert));
        final SerializedIdentity builtIdentity = identity.build();
        return builtIdentity.toByteArray();
    }

    // Used by tests to control which serialized identity is returned by
    // buildSerializedIdentity
    public void setCertificate(final String certificateToTest) {
        this.certificate = certificateToTest;
    }

    @Override
    public String getMspId() {
        return "Org1MSP";
    }
}

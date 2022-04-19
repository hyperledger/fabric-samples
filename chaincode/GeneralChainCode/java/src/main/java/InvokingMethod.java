/*
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.inject.internal.util.Lists;
import lombok.extern.java.Log;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import java.util.ArrayList;
import java.util.List;


@Log
public class InvokingMethod {

    @Transaction
    public static QueryResultList richQuery(final Context ctx, String s) {
        QueryResultList resultList = new QueryResultList();
        QueryResultsIterator<KeyValue> queryResult = ctx.getStub().getQueryResult(s);
        List<QueryResult> results = Lists.newArrayList();
        if (! IterableUtils.isEmpty(queryResult)) {
            for (KeyValue kv : queryResult) {
                QueryResult Result = new QueryResult();
                Result.setKey(kv.getKey());
                Result.setJson(kv.getStringValue());
                results.add(Result);
            }
            resultList.setResultList(results);
        }
        return resultList;
    }

    @Transaction()
    public static QueryResultList queryAllByKey(final Context ctx, String key) {
        ChaincodeStub stub = ctx.getStub();
        final String startKey = key+"1";
        final String endKey = key+"99";
        QueryResultList resultList = new QueryResultList();
        QueryResultsIterator<KeyValue> queryResult = stub.getStateByRange(startKey, endKey);
        List<QueryResult> results = Lists.newArrayList();
        if (! IterableUtils.isEmpty(queryResult)) {
            for (KeyValue kv: queryResult) {
                QueryResult Result = new QueryResult();
                Result.setKey(kv.getKey());
                Result.setJson(kv.getStringValue());
                results.add(Result);
            }
            resultList.setResultList(results);
        }
        return resultList;
    }




    @Transaction
    public static String queryData(final Context ctx, final String key) {
        ChaincodeStub stub = ctx.getStub();
        String State = stub.getStringState(key);
        if (StringUtils.isBlank(State)) {
            String errorMessage = String.format("key: %s does not exist", key);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }
        return State;
    }

    @Transaction
    public static String createData(final Context ctx, final String key,String json) {
        ChaincodeStub stub = ctx.getStub();
        String teaAreaState = stub.getStringState(key);
        if (StringUtils.isNotBlank(teaAreaState)) {
            String errorMessage = String.format("key: %s already exists", key);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }
        stub.putStringState(key, json);
        return json;
    }

    @Transaction
    public static String deleteData(final Context ctx,final String key){
        ChaincodeStub stub = ctx.getStub();
        String state = stub.getStringState(key);
        if (StringUtils.isBlank(state)){
            String errorMessage = String.format("Key %s does not exist",key);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }
        stub.delState(key);
        return state;
    }

    @Transaction
    public static String updateData(final Context ctx, final String key,String json) {
        ChaincodeStub stub = ctx.getStub();
        String state = stub.getStringState(key);
        if (StringUtils.isBlank(state)) {
            String errorMessage = String.format("key: %s does exists", key);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }
        stub.putStringState(key, json);
        return json;
    }




}

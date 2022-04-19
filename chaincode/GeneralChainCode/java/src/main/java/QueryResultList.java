/*
 * SPDX-License-Identifier: Apache-2.0
 */
import lombok.Data;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import java.util.List;

@DataType
@Data
public class QueryResultList {

    @Property
    List<QueryResult> resultList;
}

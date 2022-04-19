/*
 * SPDX-License-Identifier: Apache-2.0
 */
import lombok.Data;
import lombok.experimental.Accessors;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType()
@Data
@Accessors(chain = true)
public class QueryResult {
    @Property()
    private String key;

    @Property()
    private String json;
}

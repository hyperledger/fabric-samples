/*
 *  Copyright CGB Corp All Rights Reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.cgb.bcpinstall.db.util.object;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Column implements Serializable {

    private static final long serialVersionUID = 4765664012786336010L;
    private String            columnName;
    private String            type;
    private String            fieldName;
    private String            dbType;
    private int               length;
    private boolean           isNull;
    private boolean           isUnique;
    private boolean           isMaster;
    private boolean           isLike;

    public Column(String columnName, String type, String fieldName, String dbType){
        super();
        this.columnName = columnName;
        this.type = type;
        this.fieldName = fieldName;
        this.dbType = dbType;
    }
    public Column(String columnName, String type, String fieldName, String dbType,boolean isLike){
        super();
        this.columnName = columnName;
        this.type = type;
        this.fieldName = fieldName;
        this.dbType = dbType;
        this.isLike = isLike;
    }

    public Column(String columnName, String type, String fieldName, String dbType, int length, boolean isNull,
                  boolean isUnique,boolean isMaster){
        super();
        this.columnName = columnName;
        this.type = type;
        this.fieldName = fieldName;
        this.dbType = dbType;
        this.length = length;
        this.isNull = isNull;
        this.isUnique = isUnique;
        this.isMaster = isMaster;
    }

    public Column(){
        super();
    }

    @Override
    public String toString() {
        return "Column [columnName=" + columnName + ", type=" + type + ", fieldName=" + fieldName + ", dbType="
                + dbType + ", length=" + length + ", isNull=" + isNull + "]";
    }

}
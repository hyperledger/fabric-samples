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
package com.cgb.bcpinstall.db.util.object;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class Table implements Serializable {

    private static final long serialVersionUID = 1093724215557902949L;
    private String            tableName;
    private String            className;
    private List<Column> fields;

    public Table(String tableName, String className, List<Column> fields){
        super();
        this.tableName = tableName;
        this.className = className;
        this.fields = fields;
    }

    public Table(){
        super();
    }

    @Override
    public String toString() {
        return "Table [tableName=" + tableName + ", className=" + className + ", fields=" + fields + "]";
    }

}

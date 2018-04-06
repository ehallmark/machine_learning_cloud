package seeding.google.postgres.query_helper.appliers;

import data_pipeline.helpers.Function2;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DefaultApplier implements Function2<PreparedStatement,List<Object>,Void> {

    private final boolean conflictClause;
    private final Connection conn;
    private final String[] fields;
    public DefaultApplier(boolean conflictClause, Connection conn, String[] fields) {
        this.conflictClause=conflictClause;
        this.conn=conn;
        this.fields=fields;
    }

    @Override
    public Void apply(PreparedStatement preparedStatement, List<Object> data) {
        int n = data.size();
        try {
            for (int i = 0; i < n; i++) {
                String field = fields[i];
                setObject(preparedStatement, field,i + 1, data.get(i),conn);
                if (conflictClause && i != 0) {
                    setObject(preparedStatement, field,n + i, data.get(i),conn);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void setObject(PreparedStatement preparedStatement, String field, int idx, Object data, Connection conn) throws Exception {
        if(data == null) {
            preparedStatement.setObject(idx, null);
        } else if(data instanceof String) {
            if(field.endsWith("_date")||field.endsWith("Date")) {
                // is a date
                if(data.equals("0")) {
                    preparedStatement.setObject(idx, null);
                } else {
                    LocalDate date;
                    if(((String)data).contains("-")) {
                        date = LocalDate.parse((String)data,DateTimeFormatter.ISO_DATE);
                    } else {
                        date = LocalDate.parse((String)data,DateTimeFormatter.BASIC_ISO_DATE);
                    }
                    preparedStatement.setDate(idx, Date.valueOf(date));
                }
            } else {
                preparedStatement.setString(idx, (String) data);
            }
        } else if (data instanceof Integer) {
            preparedStatement.setInt(idx,(Integer)data);
        } else if (data instanceof Double) {
            preparedStatement.setDouble(idx,(Double)data);
        } else if (data instanceof Float) {
            preparedStatement.setFloat(idx,(Float)data);
        } else if (data instanceof Long) {
            preparedStatement.setLong(idx,(Long)data);
        } else if (data instanceof Boolean) {
            preparedStatement.setBoolean(idx, (Boolean) data);
        } else if (data instanceof LocalDate) {
            preparedStatement.setDate(idx, Date.valueOf((LocalDate)data));
        } else if (data instanceof Date) {
            preparedStatement.setDate(idx, (Date)data);
        } else if (data instanceof String[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("varchar",(Object[])data));
        } else if (data instanceof Double[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("float8",(Object[])data));
        } else if (data instanceof Float[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("float4",(Object[])data));
        } else if (data instanceof Integer[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("int4",(Object[])data));
        } else if (data instanceof Long[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("int8",(Object[])data));
        } else if (data instanceof Boolean[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("bool",(Object[])data));
        } else if (data instanceof double[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("float8",toPrimitive((double[])data)));
        } else if (data instanceof float[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("float4",toPrimitive((float[])data)));
        } else if (data instanceof int[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("int4",toPrimitive((int[])data)));
        } else if (data instanceof long[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("int8",toPrimitive((long[])data)));
        } else if (data instanceof boolean[]) {
            preparedStatement.setArray(idx,conn.createArrayOf("bool",toPrimitive((boolean[])data)));
        }
    }


    private static Boolean[] toPrimitive(boolean[] data) {
        Boolean[] tmp = new Boolean[data.length];
        for(int i = 0; i < tmp.length; i++) {
            tmp[i]=data[i];
        }
        return tmp;
    }


    private static Long[] toPrimitive(long[] data) {
        Long[] tmp = new Long[data.length];
        for(int i = 0; i < tmp.length; i++) {
            tmp[i]=data[i];
        }
        return tmp;
    }

    private static Float[] toPrimitive(float[] data) {
        Float[] tmp = new Float[data.length];
        for(int i = 0; i < tmp.length; i++) {
            tmp[i]=data[i];
        }
        return tmp;
    }

    private static Integer[] toPrimitive(int[] data) {
        Integer[] tmp = new Integer[data.length];
        for(int i = 0; i < tmp.length; i++) {
            tmp[i]=data[i];
        }
        return tmp;
    }

    private static Double[] toPrimitive(double[] data) {
        Double[] tmp = new Double[data.length];
        for(int i = 0; i < tmp.length; i++) {
            tmp[i]=data[i];
        }
        return tmp;
    }
}

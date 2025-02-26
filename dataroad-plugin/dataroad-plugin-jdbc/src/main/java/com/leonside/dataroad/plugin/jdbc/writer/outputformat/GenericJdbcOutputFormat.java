package com.leonside.dataroad.plugin.jdbc.writer.outputformat;

import com.google.gson.Gson;
import com.leonside.dataroad.common.domain.ColumnType;
import com.leonside.dataroad.common.enums.WriteMode;
import com.leonside.dataroad.common.exception.WriteRecordException;
import com.leonside.dataroad.common.utils.ClassUtil;
import com.leonside.dataroad.common.utils.DateUtil;
import com.leonside.dataroad.common.utils.ExceptionUtil;
import com.leonside.dataroad.flink.writer.outputformat.GenericRichOutputFormat;
import com.leonside.dataroad.flink.restore.FormatState;
import com.leonside.dataroad.plugin.jdbc.DatabaseDialect;
import com.leonside.dataroad.plugin.jdbc.type.TypeConverterInterface;
import com.leonside.dataroad.plugin.jdbc.utils.DbUtil;
import com.leonside.dataroad.plugin.jdbc.writer.config.JdbcWriterConfig;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * OutputFormat for writing data to relational database.
 *
 */
public class GenericJdbcOutputFormat extends GenericRichOutputFormat {

    protected static final Logger LOG = LoggerFactory.getLogger(GenericJdbcOutputFormat.class);

    protected static final long serialVersionUID = 1L;

    public JdbcWriterConfig jdbcWriterConfig;

    protected String driverName;

    protected Connection dbConn;

    protected PreparedStatement preparedStatement;

    protected DatabaseDialect databaseDialect;

    protected String mode ;

    /**just for postgresql,use copy replace insert*/
    protected String insertSqlMode;

    protected Map<String, String> fullColumnMapping = new HashMap<>();

    protected List<String> fullColumnType;

    protected List<String> columnType = new ArrayList<>();

    protected TypeConverterInterface typeConverter;

    protected Row lastRow = null;

    protected boolean readyCheckpoint;

    protected long rowsOfCurrentTransaction;

    protected String primaryKey;

    /**
     * schema名
     */
    public String schema;

    protected final static String GET_INDEX_SQL = "SELECT " +
            "t.INDEX_NAME," +
            "t.COLUMN_NAME " +
            "FROM " +
            "user_ind_columns t," +
            "user_indexes i " +
            "WHERE " +
            "t.index_name = i.index_name " +
            "AND i.uniqueness = 'UNIQUE' " +
            "AND t.table_name = '%s'";

    protected final static String CONN_CLOSE_ERROR_MSG = "No operations allowed";
    protected static List<String> STRING_TYPES = Arrays.asList("CHAR", "VARCHAR", "VARCHAR2", "NVARCHAR2", "NVARCHAR", "TINYBLOB","TINYTEXT","BLOB","TEXT", "MEDIUMBLOB", "MEDIUMTEXT", "LONGBLOB", "LONGTEXT");

    protected PreparedStatement prepareTemplates() throws SQLException {
        if(CollectionUtils.isEmpty(jdbcWriterConfig.getFullColumn())) {
            jdbcWriterConfig.setFullColumn(jdbcWriterConfig.getColumn());
        }

        String singleSql;
        if (WriteMode.INSERT.name().equalsIgnoreCase(mode) || WriteMode.STREAM.name().equalsIgnoreCase(mode)) {
            singleSql = databaseDialect.getInsertStatement(jdbcWriterConfig.getColumn(), jdbcWriterConfig.getTable());
        } else if (WriteMode.REPLACE.name().equalsIgnoreCase(mode)) {
            singleSql = databaseDialect.getReplaceStatement(jdbcWriterConfig.getColumn(), jdbcWriterConfig.getFullColumn(), jdbcWriterConfig.getTable(), jdbcWriterConfig.getUpdateKey());
        } else if (WriteMode.UPDATE.name().equalsIgnoreCase(mode)) {
            singleSql = databaseDialect.getUpsertStatement(jdbcWriterConfig.getColumn(), jdbcWriterConfig.getTable(), jdbcWriterConfig.getUpdateKey());
        } else {
            throw new IllegalArgumentException("Unknown write mode:" + mode);
        }

        LOG.info("write sql:{}", singleSql);

        return dbConn.prepareStatement(singleSql);
    }

    @Override
    protected void doOpen(int taskNumber, int numTasks){
        try {
            ClassUtil.forName(driverName, getClass().getClassLoader());
            dbConn = DbUtil.getConnection(jdbcWriterConfig.getJdbcUrl(), jdbcWriterConfig.getUsername(), jdbcWriterConfig.getPassword());

            //默认关闭事务自动提交，手动控制事务
            dbConn.setAutoCommit(false);

            if(CollectionUtils.isEmpty(jdbcWriterConfig.getFullColumn())) {
                jdbcWriterConfig.setFullColumn(probeFullColumns(getTable(), dbConn));
            }

            if (!WriteMode.INSERT.name().equalsIgnoreCase(mode)){
                if(jdbcWriterConfig.getUpdateKey() == null || jdbcWriterConfig.getUpdateKey().size() == 0) {
                    jdbcWriterConfig.setUpdateKey(probePrimaryKeys(getTable(), dbConn));
                }
            }

            if(fullColumnType == null) {
                fullColumnType = analyzeTable();
                analyzePrimaryKeys();
            }

            for(String col : jdbcWriterConfig.getColumn()) {
                for (int i = 0; i < jdbcWriterConfig.getFullColumn().size(); i++) {
                    fullColumnMapping.put(jdbcWriterConfig.getFullColumn().get(i), fullColumnType.get(i));
                    if (col.equalsIgnoreCase(jdbcWriterConfig.getFullColumn().get(i))){
                        columnType.add(fullColumnType.get(i));
                        break;
                    }
                }
            }

            preparedStatement = prepareTemplates();
            readyCheckpoint = false;


            LOG.info("subTask[{}}] wait finished", taskNumber);
        } catch (SQLException sqe) {
            throw new IllegalArgumentException("open() failed.", sqe);
        }finally {
            DbUtil.commit(dbConn);
        }
    }

    protected void analyzePrimaryKeys() throws SQLException {
    }

    protected List<String> analyzeTable() {
        List<String> ret = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = dbConn.createStatement();
            rs = stmt.executeQuery(databaseDialect.getSqlQueryFields(databaseDialect.quoteTable(jdbcWriterConfig.getTable())));
            ResultSetMetaData rd = rs.getMetaData();

            for(int i = 0; i < rd.getColumnCount(); ++i) {
                ret.add(rd.getColumnTypeName(i+1));
            }

            if(CollectionUtils.isEmpty(jdbcWriterConfig.getFullColumn())){
                for(int i = 0; i < rd.getColumnCount(); ++i) {
                    jdbcWriterConfig.getFullColumn().add(rd.getColumnName(i+1));
                }
            }
            if(CollectionUtils.isEmpty(jdbcWriterConfig.getColumn())){
                jdbcWriterConfig.setColumn(jdbcWriterConfig.getFullColumn());
            }

            analyzePrimaryKeys();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            DbUtil.closeDbResources(rs, stmt,null, false);
        }

        return ret;
    }

    @Override
    protected void doWriteSingleRecord(Row row) throws WriteRecordException {
        int index = 0;
        try {
            for (; index < this.jdbcWriterConfig.getColumn().size(); index++) {
                preparedStatement.setObject(index+1, getField(row, this.jdbcWriterConfig.getColumn().get(index)));
            }

            preparedStatement.execute();
            DbUtil.commit(dbConn);
        } catch (Exception e) {
            DbUtil.rollBack(dbConn);
            processWriteException(e, index, row);
        }
    }

    protected void processWriteException(Exception e, int index, Row row) throws WriteRecordException{
        if(e instanceof SQLException){
            if(e.getMessage().contains(CONN_CLOSE_ERROR_MSG)){
                throw new RuntimeException("Connection maybe closed", e);
            }
        }

        if(index < row.getArity()) {
            String message = recordConvertDetailErrorMessage(index, row);
            LOG.error(message, e);
            throw new WriteRecordException(index, message, e);
        }
        throw new WriteRecordException(index, e.getMessage(), e);
    }

    @Override
    protected String recordConvertDetailErrorMessage(int pos, Row row) {
        return "\nJdbcOutputFormat [" + jobName + "] writeRecord error: when converting field[" + pos + "] in Row(" + row + ")";
    }

    @Override
    protected void doWriteMultipleRecords() throws Exception {
        try {

            for (Row row : rows) {
                for (int index = 0; index < this.jdbcWriterConfig.getColumn().size(); index++) {
                    preparedStatement.setObject(index+1, getField(row, this.jdbcWriterConfig.getColumn().get(index)));
                }
                preparedStatement.addBatch();

                if (restoreConfig.isRestore()) {
                    if (lastRow != null){
                        readyCheckpoint = !ObjectUtils.equals(lastRow.getField(restoreConfig.getRestoreColumnName()),
                                row.getField(restoreConfig.getRestoreColumnName()));
                    }

                    lastRow = row;
                }
            }

            preparedStatement.executeBatch();

            if(restoreConfig.isRestore()){
                rowsOfCurrentTransaction += rows.size();
            }else{
                //手动提交事务
                DbUtil.commit(dbConn);
            }
            preparedStatement.clearBatch();
        } catch (Exception e){
            LOG.warn("write Multiple Records error, row size = {}, first row = {},  e = {}",
                    rows.size(),
                    rows.size() > 0 ? new Gson().toJson(rows.get(0)) : "null",
                    ExceptionUtil.getErrorMessage(e));
            LOG.warn("error to writeMultipleRecords, start to rollback connection, e = {}", ExceptionUtil.getErrorMessage(e));
            DbUtil.rollBack(dbConn);
            throw e;
        }finally {
            //执行完后清空batch
            preparedStatement.clearBatch();
        }
    }

    @Override
    public FormatState getFormatState(){
        if (!restoreConfig.isRestore() || lastRow == null){
            LOG.info("return null for formatState");
            return null;
        }

        try {
            LOG.info("readyCheckpoint: {}, rowsOfCurrentTransaction: {}", readyCheckpoint, rowsOfCurrentTransaction);

            if (readyCheckpoint || rowsOfCurrentTransaction > restoreConfig.getMaxRowNumForCheckpoint()){

                LOG.info("getFormatState:Start commit connection");
                if(rows != null && rows.size() > 0){
                    super.doWriteRecord();
                }else{
                    preparedStatement.executeBatch();
                }
                //若事务提交失败，抛出异常
                dbConn.commit();
                preparedStatement.clearBatch();
                LOG.info("getFormatState:Commit connection success");

                snapshotWriteCounter.add(rowsOfCurrentTransaction);
                numWriteCounter.add(rowsOfCurrentTransaction);
                rowsOfCurrentTransaction = 0;

                formatState.setState(lastRow.getField(restoreConfig.getRestoreColumnName()));
                formatState.setNumberWrite(snapshotWriteCounter.getLocalValue());
                LOG.info("format state:{}", formatState.getState());

                super.getFormatState();
                return formatState;
            }

            return null;
        } catch (Exception e){
            try {
                //执行完后清空batch
                preparedStatement.clearBatch();
                LOG.warn("getFormatState:Start rollback");
                //若事务回滚失败，抛出异常
                dbConn.rollback();
                LOG.warn("getFormatState:Rollback success");
            } catch (SQLException sqlE){
                throw new RuntimeException("Rollback error:", e);
            }

            throw new RuntimeException("Return format state error:", e);
        }
    }

    /**
     * 获取转换后的字段value
     * @param row
     * @return
     */
    protected Object getField(Row row, String name) {
        Object field = row.getField(name);
        String type = fullColumnMapping.get(name);

        //field为空字符串，且写入目标类型不为字符串类型的字段，则将object设置为null
        if(field instanceof String
                && StringUtils.isBlank((String) field)
                &&!STRING_TYPES.contains(type.toUpperCase(Locale.ENGLISH))){
            return null;
        }

        if(type.matches(DateUtil.DATE_REGEX)) {
            field = DateUtil.columnToDate(field,null);
        } else if(type.matches(DateUtil.DATETIME_REGEX) || type.matches(DateUtil.TIMESTAMP_REGEX)){
            field = DateUtil.columnToTimestamp(field,null);
        }

        if (type.equalsIgnoreCase(ColumnType.BIGINT.name()) && field instanceof java.util.Date){
            field = ((java.util.Date) field).getTime();
        }

        return field;
    }

    protected List<String> probeFullColumns(String table, Connection dbConn) throws SQLException {
        List<String> ret = new ArrayList<>();
        ResultSet rs = dbConn.getMetaData().getColumns(null, null, table, null);
        while(rs.next()) {
            ret.add(rs.getString("COLUMN_NAME"));
        }
        return ret;
    }

    protected Map<String, List<String>> probePrimaryKeys(String table, Connection dbConn) throws SQLException {
        Map<String, List<String>> map = new HashMap<>(16);
        ResultSet rs = dbConn.getMetaData().getIndexInfo(null, null, table, true, false);
        while(rs.next()) {
            String indexName = rs.getString("INDEX_NAME");
            if(!map.containsKey(indexName)) {
                map.put(indexName,new ArrayList<>());
            }
            map.get(indexName).add(rs.getString("COLUMN_NAME"));
        }
        Map<String,List<String>> retMap = new HashMap<>((map.size()<<2)/3);
        for(Map.Entry<String,List<String>> entry: map.entrySet()) {
            String k = entry.getKey();
            List<String> v = entry.getValue();
            if(v!=null && v.size() != 0 && v.get(0) != null) {
                retMap.put(k, v);
            }
        }
        return retMap;
    }

    @Override
    public void doClose() {
        readyCheckpoint = false;
        boolean commit = true;
        try{
            numWriteCounter.add(rowsOfCurrentTransaction);
            String state = getTaskState();
            // Do not commit a transaction when the task is canceled or failed
            if(!RUNNING_STATE.equals(state) && restoreConfig.isRestore()){
                commit = false;
            }
        } catch (Exception e){
            LOG.error("Get task status error:{}", e.getMessage());
        }

        DbUtil.closeDbResources(null, preparedStatement, dbConn, commit);
        dbConn = null;
    }

    @Override
    protected boolean needWaitBeforeWriteRecords() {
        return  CollectionUtils.isNotEmpty(jdbcWriterConfig.getPreSql());
    }

    @Override
    protected void beforeWriteRecords()  {
        // preSql
        if(taskNumber == 0) {
            DbUtil.executeBatch(dbConn, jdbcWriterConfig.getPreSql());
        }
    }

    @Override
    protected boolean doNeedWaitBeforeClose() {
        return  CollectionUtils.isNotEmpty(jdbcWriterConfig.getPostSql());
    }

    @Override
    protected void beforeCloseInternal() {
        // 执行postSql
        if(taskNumber == 0) {
            DbUtil.executeBatch(dbConn, jdbcWriterConfig.getPostSql());
        }
    }

    /**
     * 获取table名称，如果table是schema.table格式，可重写此方法 只返回table
     * @return
     */
    protected String getTable(){
        return jdbcWriterConfig.getTable();
    }

    public void setSchema(String schema){
        this.schema = schema;
    }
}

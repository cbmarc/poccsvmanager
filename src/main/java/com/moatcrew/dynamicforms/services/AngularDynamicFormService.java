package com.moatcrew.dynamicforms.services;

import com.moatcrew.dynamicforms.models.Column;
import com.moatcrew.dynamicforms.models.ForeignKey;
import com.moatcrew.dynamicforms.models.Table;
import org.json.JSONArray;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by maruku on 13/04/16.
 */
@Service
public class AngularDynamicFormService implements DynamicFormService<JSONArray> {

    private static Logger LOG = Logger.getLogger(AngularDynamicFormService.class.getName());

    /**
     * Patters
     */
    private static Pattern TABLES_PATTERN = Pattern.compile("create table.*?;", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    private static Pattern TABLE_NAME_PATTERN = Pattern.compile("create table ([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static Pattern COLUMN_NAME_PATTERN = Pattern.compile("(^(?!primary key)[A-Za-z0-9_]+)", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    private static Pattern COLUMN_TYPE_PATTERN = Pattern.compile("^(?!primary key).*?\\s([A-Za-z0-9]+)[\\s|\\(]", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    private static Pattern COLUMN_NOTNULL_PATTERN = Pattern.compile("^(?!primary key).*?(not null)", Pattern.CASE_INSENSITIVE);
    private static Pattern ALTER_TABLE_PATTERN = Pattern.compile("alter table.*?;", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    private static Pattern ALTER_TABLE_NAME_PATTERN = Pattern.compile("alter table\\s([A-Za-z0-9_]+)\\n", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    private static Pattern ALTER_TABLE_FK_CHUNK_PATTERN = Pattern.compile("foreign key\\s\\((.*?)\\)", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    private static Pattern ALTER_TABLE_FK_REFERENCES_PATTERN = Pattern.compile("references\\s(.*?);", Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
    // TODO Add primary key parsing, not needed for first POC

    private Map<String, Table> tablesCache;

    public JSONArray getForm(String tableName) {
        return null;
    }

    public void initializeForms(String sourceFilePath) throws IOException {
        loadTables(sourceFilePath);
    }

    private void loadTables(String sourceFilePath) throws IOException {
        String fileContents = getFileContents(sourceFilePath);
        Matcher matcher = TABLES_PATTERN.matcher(fileContents.trim());
        List<String> creates = new ArrayList<String>();
        while (matcher.find()) {
            creates.add(matcher.group(0));
        }
        tablesCache = new HashMap<String, Table>();
        for (String create : creates) {
            String[] lines = create.trim().split("\n");
            final Table table = new Table(getMatchingString(lines[0], TABLE_NAME_PATTERN));
            // Skip first and last line (create and ending)
            for (int i = 1; i < lines.length - 1; i++) {
                final String line = lines[i];
                final Column column = new Column();
                column.setName(getMatchingString(line, COLUMN_NAME_PATTERN));
                column.setType(getMatchingString(line, COLUMN_TYPE_PATTERN));
                column.setAllowsNull(StringUtils.isEmpty(getMatchingString(line, COLUMN_NOTNULL_PATTERN)));
                if (!StringUtils.isEmpty(column.getName())) {
                    table.addColumn(column);
                }
            }
            tablesCache.put(table.getName(), table);
        }
        loadForeignKeys(fileContents);
    }

    private void loadForeignKeys(String fileContents) {
        Matcher matcher = ALTER_TABLE_PATTERN.matcher(fileContents);
        while (matcher.find()) {
            final String alterTable = matcher.group(0);
            final String tableName = getMatchingString(alterTable, ALTER_TABLE_NAME_PATTERN);
            final String references = getMatchingString(alterTable, ALTER_TABLE_FK_REFERENCES_PATTERN);
            final String fksChunk = getMatchingString(alterTable, ALTER_TABLE_FK_CHUNK_PATTERN);
            final String[] fks = fksChunk.split(",");
            for (String fk : fks) {
                final Table table = tablesCache.get(tableName);
                final Table targetTable = tablesCache.get(references);
                final Column column = table.getColumns().get(fk);
//                final Column targetColumn = targetTable.getColumns().get(fk);
//                column.setForeignKey(new ForeignKey(targetTable, targetColumn));
//                System.out.println("blah");
            }
        }

    }

    private String getMatchingString(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line.trim());
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            LOG.info("No matches for pattern:\n " + pattern.pattern() + "\nOn line:\n" + line);
        }
        return "";
    }

    String getFileContents(String sourceFilePath) throws IOException {
        File file = new File(sourceFilePath);
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        return new String(data, "UTF-8");
    }

    public Map<String, Table> getTablesCache() {
        return tablesCache;
    }
}

/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.test.it.dxl;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.TableIndex;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import com.akiban.server.api.ddl.UnsupportedDropException;
import com.akiban.server.service.tree.TreeLink;
import com.akiban.server.test.it.ITBase;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public final class DropTreesIT extends ITBase {
    private boolean treeExists(TreeLink link) throws Exception {
        return serviceManager().getTreeService().treeExists(link.getSchemaName(), link.getTreeName());
    }

    private TreeLink treeLink(Object o) {
        if(o == null) throw new IllegalArgumentException("TreeLink holder is null");
        if(o instanceof Table) return (TreeLink) ((Table)o).rowDef();
        if(o instanceof Index) return (TreeLink) ((Index)o).indexDef();
        throw new IllegalArgumentException("Unknown TreeLink holder: " + o);
    }

    private void expectTree(Object hasTreeLink) throws Exception {
        TreeLink link = treeLink(hasTreeLink);
        assertTrue("tree should exist: " + link.getTreeName(), treeExists(link));
    }

    private void expectNoTree(Object hasTreeLink) throws Exception {
        TreeLink link = treeLink(hasTreeLink);
        assertFalse("tree should not exist: " + link.getTreeName(), treeExists(link));
    }

    private static Index createSimpleIndex(Table curTable, String columnName) {
        AkibanInformationSchema ais = new AkibanInformationSchema();
        Table newTable = UserTable.create(ais, curTable.getName().getSchemaName(), curTable.getName().getTableName(), 0);
        Index newIndex = TableIndex.create(ais, newTable, columnName, 0, false, Index.KEY_CONSTRAINT);
        Column curColumn = curTable.getColumn(columnName);
        Column newColumn = Column.create(newTable,  curColumn.getName(), curColumn.getPosition(), curColumn.getType());
        newColumn.setTypeParameter1(curColumn.getTypeParameter1());
        newColumn.setTypeParameter2(curColumn.getTypeParameter2());
        newIndex.addColumn(new IndexColumn(newIndex, newColumn, 0, true, null));
        return newIndex;
    }

    
    @Test
    public void singleTableNoData() throws Exception {
        int tid = createTable("s", "t", "id int key");
        Table t = getUserTable(tid);
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
    }

    @Test
    public void singleTableNoDataRepeatedly() throws Exception {
        final TableName name = new TableName("s", "t");
        for(int i = 1; i <= 5; ++i) {
            try {
                int tid = createTable(name.getSchemaName(), name.getTableName(), "id int key");
                Table t = getUserTable(tid);
                ddl().dropTable(session(), name);
                expectNoTree(t);
            } catch(Exception e) {
                throw new Exception("Failed on iteration: " + i, e);
            }
        }
    }

    @Test
    public void singleTableWithData() throws Exception {
        int tid = createTable("s", "t", "id int key, name varchar(32)");
        Table t = getUserTable(tid);
        writeRows(createNewRow(tid, 1L, "joe"),
                  createNewRow(tid, 2L, "bob"),
                  createNewRow(tid, 3L, "jim"));
        expectTree(t);
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
    }

    @Test
    public void groupedTablesNoData() throws Exception {
        int pid = createTable("s", "p", "id int key");
        int cid = createTable("s", "c", "id int key, pid int, constraint __akiban foreign key(pid) references p(id))");
        Table p = getUserTable(pid);
        Table c = getUserTable(cid);
        ddl().dropTable(session(), c.getName());
        ddl().dropTable(session(), p.getName());
        expectNoTree(p);
        expectNoTree(c);
    }

    @Test
    public void groupedTablesWithData() throws Exception {
        int pid = createTable("s", "p", "id int key");
        int cid = createTable("s", "c", "id int key, pid int, constraint __akiban foreign key(pid) references p(id))");
        writeRows(createNewRow(pid, 1L),
                  createNewRow(pid, 2L),
                  createNewRow(pid, 3L));
        writeRows(createNewRow(cid, 10L, 1L),
                  createNewRow(cid, 20L, 1L),
                  createNewRow(cid, 30L, 2L));
        Table p = getUserTable(pid);
        expectTree(p);
        Table c = getUserTable(cid);
        expectTree(c);
        ddl().dropTable(session(), c.getName());
        ddl().dropTable(session(), p.getName());
        expectNoTree(p);
        expectNoTree(c);
    }

    @Test
    public void secondaryIndexNoData() throws Exception {
        int tid = createTable("s", "t", "id int key, c char(10), index c(c)");
        Table t = getUserTable(tid);
        Index c = t.getIndex("c");
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
        expectNoTree(c);
    }

    @Test
    public void secondaryIndexWithData() throws Exception {
        int tid = createTable("s", "t", "id int key, c char(10), index c(c)");
        writeRows(createNewRow(tid, 1L, "abcd"),
                  createNewRow(tid, 2L, "efgh"),
                  createNewRow(tid, 3L, "ijkl"));
        Table t = getUserTable(tid);
        expectTree(t);
        Index c = t.getIndex("c");
        expectTree(c);
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
        expectNoTree(c);
    }

    @Test
    public void addSecondaryIndexNoData() throws Exception {
        int tid = createTable("s", "t", "id int key, other int");
        Table t = getUserTable(tid);
        ddl().createIndexes(session(), Collections.singleton(createSimpleIndex(t, "other")));
        t = getUserTable(tid);
        Index other = t.getIndex("other");
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
        expectNoTree(other);
    }

    @Test
    public void addSecondaryIndexWithData() throws Exception {
        int tid = createTable("s", "t", "id int key, other int");
        writeRows(createNewRow(tid, 1L, 10L),
                  createNewRow(tid, 2L, 20L),
                  createNewRow(tid, 3L, 30L));
        Table t = getUserTable(tid);
        expectTree(t);
        ddl().createIndexes(session(), Collections.singleton(createSimpleIndex(t, "other")));
        t = getUserTable(tid);
        expectTree(t);
        Index other = t.getIndex("other");
        expectTree(other);
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
        expectNoTree(other);
    }

    @Test
    public void dropSecondaryIndexNoData() throws Exception {
        int tid = createTable("s", "t", "id int key, c char(10), index c(c), key c(c)");
        Table t = getUserTable(tid);
        Index c = t.getIndex("c");
        ddl().dropTableIndexes(session(), t.getName(), Collections.singleton("c"));
        expectNoTree(c);
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
    }
    
    @Test
    public void dropSecondaryIndexWithData() throws Exception {
        int tid = createTable("s", "t", "id int key, c char(10), index c(c), key c(c)");
        writeRows(createNewRow(tid, 1L, "mnop"),
                  createNewRow(tid, 2L, "qrst"),
                  createNewRow(tid, 3L, "uvwx"));
        Table t = getUserTable(tid);
        expectTree(t);
        Index c = t.getIndex("c");
        expectTree(c);
        ddl().dropTableIndexes(session(), t.getName(), Collections.singleton("c"));
        expectNoTree(c);
        expectTree(t);
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
    }

    @Test
    public void childSecondaryIndexNoData() throws Exception {
        int pid = createTable("s", "p", "id int key");
        int cid = createTable("s", "c", "id int key, i int, pid int, key i(i), constraint __akiban foreign key(pid) references p(id))");
        Table p = getUserTable(pid);
        Table c = getUserTable(cid);
        Index i = c.getIndex("i");
        ddl().dropTable(session(), c.getName());
        expectNoTree(i);
        ddl().dropTable(session(), p.getName());
        expectNoTree(p);
    }

    @Test
    public void childSecondaryIndexWithData() throws Exception {
        int pid = createTable("s", "p", "id int key");
        int cid = createTable("s", "c", "id int key, i int, pid int, key i(i), constraint __akiban foreign key(pid) references p(id))");
        writeRows(createNewRow(pid, 1L),
                  createNewRow(pid, 2L));
        Table p = getUserTable(pid);
        expectTree(p);
        writeRows(createNewRow(cid, 10L, 100L, 1L),
                  createNewRow(cid, 20L, 100L, 2L));
        Table c = getUserTable(cid);
        expectTree(c);
        Index i = c.getIndex("i");
        expectTree(i);
        ddl().dropTable(session(), c.getName());
        expectNoTree(i);
        ddl().dropTable(session(), p.getName());
        expectNoTree(p);
    }

    @Test
    public void pkLessRootNoData() throws Exception {
        int tid = createTable("s", "t", "i int, key i(i)");
        UserTable t = getUserTable(tid);
        Index pk = t.getIndexIncludingInternal(Index.PRIMARY_KEY_CONSTRAINT);
        ddl().dropTable(session(), t.getName());
        expectNoTree(pk);
        expectNoTree(t);
    }

    @Test
    public void pkLessRootWithData() throws Exception {
        int tid = createTable("s", "t", "i int, key i(i)");
        writeRows(createNewRow(tid, 10L, 0L),
                  createNewRow(tid, 20L, 0L));
        UserTable t = getUserTable(tid);
        expectTree(t);
        Index pk = t.getIndexIncludingInternal(Index.PRIMARY_KEY_CONSTRAINT);
        if(!pk.isHKeyEquivalent()) {
            expectTree(pk);
        }
        ddl().dropTable(session(), t.getName());
        expectNoTree(t);
        expectNoTree(pk);
    }

    @Test
    public void pkLessChildNoData() throws Exception {
        int pid = createTable("s", "p", "id int key");
        int cid = createTable("s", "c", "i int, pid int, key i(i), constraint __akiban foreign key(pid) references p(id))");
        Table p = getUserTable(pid);
        UserTable c = getUserTable(cid);
        Index pk = c.getIndexIncludingInternal(Index.PRIMARY_KEY_CONSTRAINT);
        ddl().dropTable(session(), c.getName());
        expectNoTree(pk);
        ddl().dropTable(session(), p.getName());
        expectNoTree(c);
        expectNoTree(p);
    }

    @Test
    public void pkLessChildTableWithData() throws Exception {
        int pid = createTable("s", "p", "id int key");
        int cid = createTable("s", "c", "i int, pid int, key i(i), constraint __akiban foreign key(pid) references p(id))");
        writeRows(createNewRow(pid, 1L),
                  createNewRow(pid, 2L));
        Table p = getUserTable(pid);
        expectTree(p);
        writeRows(createNewRow(cid, 10L, 1L, 0L),
                  createNewRow(cid, 20L, 2L, 0L));
        UserTable c = getUserTable(cid);
        expectTree(c);
        Index pk = c.getIndexIncludingInternal(Index.PRIMARY_KEY_CONSTRAINT);
        if(!pk.isHKeyEquivalent()) {
            expectTree(pk);
        }
        ddl().dropTable(session(), c.getName());
        expectNoTree(pk);
        ddl().dropTable(session(), p.getName());
        expectNoTree(c);
        expectNoTree(p);
    }

    @Test
    public void groupedTablesWithDataTryDropParent() throws Exception {
        int pid = createTable("s", "p", "id int key, o int, key o(o)");
        int cid = createTable("s", "c", "id int key, pid int, constraint __akiban foreign key(pid) references p(id))");
        writeRows(createNewRow(pid, 1L, 100L),
                  createNewRow(pid, 2L, 200L),
                  createNewRow(pid, 3L, 300L));
        writeRows(createNewRow(cid, 10L, 1L),
                  createNewRow(cid, 20L, 1L),
                  createNewRow(cid, 30L, 2L));
        Table p = getUserTable(pid);
        expectTree(p);
        Index o = p.getIndex("o");
        expectTree(o);
        Table c = getUserTable(cid);
        expectTree(c);
        try {
            ddl().dropTable(session(), p.getName());
            fail("Expected UnsupportedDropException!");
        } catch(UnsupportedDropException e) {
            expectTree(p);
            expectTree(o);
            expectTree(c);
        }
        ddl().dropTable(session(), c.getName());
        ddl().dropTable(session(), p.getName());
        expectNoTree(p);
        expectNoTree(o);
        expectNoTree(c);
    }
}
/*
 * Copyright 2019, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.datastores.aggregation.queryengines.sql.metadata;

import static com.yahoo.elide.datastores.aggregation.queryengines.sql.metadata.SQLTable.resolveSQLDimensions;

import com.yahoo.elide.datastores.aggregation.AggregationDictionary;
import com.yahoo.elide.datastores.aggregation.metadata.models.Table;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * SQL extension of {@link Table} which also contains sql column meta data.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SQLPhysicalTable extends Table implements SQLTable {
    private Set<SQLColumn> SQLColumns;

    public SQLPhysicalTable(Class<?> cls, AggregationDictionary dictionary) {
        super(cls, dictionary);
        this.SQLColumns = resolveSQLDimensions(cls, dictionary);
    }

    @Override
    public Table getLogicalTable() {
        return this;
    }
}
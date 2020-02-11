/*
 * Copyright 2020, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.datastores.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yahoo.elide.core.exceptions.InvalidOperationException;
import com.yahoo.elide.core.filter.dialect.ParseException;
import com.yahoo.elide.core.filter.expression.FilterExpression;
import com.yahoo.elide.datastores.aggregation.example.Country;
import com.yahoo.elide.datastores.aggregation.example.PlayerStats;
import com.yahoo.elide.datastores.aggregation.filter.visitor.FilterConstraints;
import com.yahoo.elide.datastores.aggregation.filter.visitor.SplitFilterExpressionVisitor;
import com.yahoo.elide.datastores.aggregation.framework.SQLUnitTest;
import com.yahoo.elide.datastores.aggregation.query.ColumnProjection;
import com.yahoo.elide.datastores.aggregation.query.Query;
import com.yahoo.elide.datastores.aggregation.query.TimeDimensionProjection;
import com.yahoo.elide.datastores.aggregation.time.TimeGrain;
import com.yahoo.elide.request.Argument;
import com.yahoo.elide.request.Attribute;
import com.yahoo.elide.request.EntityProjection;
import com.yahoo.elide.request.Relationship;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EntityProjectionTranslatorTest extends SQLUnitTest {
    private static EntityProjection basicProjection = EntityProjection.builder()
            .type(PlayerStats.class)
            .attribute(Attribute.builder()
                    .type(long.class)
                    .name("lowScore")
                    .build())
            .attribute(Attribute.builder()
                    .type(String.class)
                    .name("overallRating")
                    .build())
            .relationship(Relationship.builder()
                    .name("country")
                    .projection(EntityProjection.builder()
                            .type(Country.class)
                            .attribute(Attribute.builder()
                                    .type(String.class)
                                    .name("name")
                                    .build())
                            .build())
                    .build())
            .build();

    @BeforeAll
    public static void init() {
        SQLUnitTest.init();
    }

    @Test
    public void testBasicTranslation() {
        EntityProjectionTranslator translator = new EntityProjectionTranslator(
                playerStatsTable,
                basicProjection,
                dictionary
        );

        Query query = translator.getQuery();

        assertEquals(playerStatsTable, query.getTable());
        assertEquals(1, query.getMetrics().size());
        assertEquals("lowScore", query.getMetrics().get(0).getAlias());
        assertEquals(2, query.getGroupByDimensions().size());

        List<ColumnProjection> dimensions = new ArrayList<>(query.getGroupByDimensions());
        assertEquals("overallRating", dimensions.get(0).getColumn().getName());
        assertEquals("country", dimensions.get(1).getColumn().getName());
    }

    @Test
    public void testWherePromotion() throws ParseException {
        FilterExpression originalFilter = filterParser.parseFilterExpression("overallRating==Good,lowScore<45",
                PlayerStats.class, false);

        EntityProjection projection = basicProjection.copyOf()
                .filterExpression(originalFilter)
                .build();

        EntityProjectionTranslator translator = new EntityProjectionTranslator(
                playerStatsTable,
                projection,
                dictionary
        );

        Query query = translator.getQuery();

        SplitFilterExpressionVisitor visitor = new SplitFilterExpressionVisitor(playerStatsTable);
        FilterConstraints constraints = originalFilter.accept(visitor);
        FilterExpression whereFilter = constraints.getWhereExpression();
        FilterExpression havingFilter = constraints.getHavingExpression();
        assertEquals(whereFilter, query.getWhereFilter());
        assertEquals(havingFilter, query.getHavingFilter());
    }

    @Test
    public void testTimeDimension() {
        EntityProjection projection = basicProjection.copyOf()
                .attribute(Attribute.builder()
                        .type(Date.class)
                        .name("recordedDate")
                        .build())
                .build();

        EntityProjectionTranslator translator = new EntityProjectionTranslator(
                playerStatsTable,
                projection,
                dictionary
        );

        Query query = translator.getQuery();

        List<TimeDimensionProjection> timeDimensions = new ArrayList<>(query.getTimeDimensions());
        assertEquals(1, timeDimensions.size());
        assertEquals("recordedDate", timeDimensions.get(0).getAlias());
        assertEquals(TimeGrain.DAY, timeDimensions.get(0).getGrain());
    }

    @Test
    public void testUnsupportedTimeGrain() {
        EntityProjection projection = basicProjection.copyOf()
                .attribute(Attribute.builder()
                        .type(Date.class)
                        .name("recordedDate")
                        .argument(Argument.builder()
                                .name("grain")
                                .value("year")
                                .build())
                        .build())
                .build();

        assertThrows(InvalidOperationException.class, () -> new EntityProjectionTranslator(
                playerStatsTable,
                projection,
                dictionary
        ));
    }
}

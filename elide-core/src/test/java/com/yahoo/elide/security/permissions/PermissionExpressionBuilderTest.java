/*
 * Copyright 2016, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.security.permissions;

import com.yahoo.elide.annotation.Include;
import com.yahoo.elide.annotation.ReadPermission;
import com.yahoo.elide.annotation.UpdatePermission;
import com.yahoo.elide.core.EntityDictionary;
import com.yahoo.elide.core.PersistentResource;
import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.security.ChangeSpec;
import com.yahoo.elide.security.checks.Check;
import com.yahoo.elide.security.checks.prefab.Role;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.persistence.Entity;
import java.util.HashMap;
import java.util.Map;

public class PermissionExpressionBuilderTest {

    private EntityDictionary dictionary;
    private PermissionExpressionBuilder builder;

    @BeforeMethod
    public void setupEntityDictionary() {
        Map<String, Class<? extends Check>> checks = new HashMap<>();
        checks.put("user has all access", Role.ALL.class);
        checks.put("user has no access", Role.NONE.class);

        dictionary = new EntityDictionary(checks);

        ExpressionResultCache cache = new ExpressionResultCache();
        builder = new PermissionExpressionBuilder(cache, dictionary);
    }

    @Test
    public void testAnyFieldExpressionText() {
        @Entity
        @Include
        @ReadPermission(expression = "user has all access AND user has no access")
        class Model { }
        dictionary.bindEntity(Model.class);

        PersistentResource resource = newResource(new Model(), Model.class);

        PermissionExpressionBuilder.Expressions expressions = builder.buildAnyFieldExpressions(
                resource,
                ReadPermission.class,
                (ChangeSpec) null);

        Assert.assertEquals(expressions.getCommitExpression().toString(),
                "READ PERMISSION WAS INVOKED ON PersistentResource { type=model, id=null }  "
                        + "FOR EXPRESSION [FIELDS(FAILURE) OR ENTITY(((user has all access WAS UNEVALUATED)) "
                        + "AND ((user has no access WAS UNEVALUATED)))]");

        expressions.getCommitExpression().evaluate();

        Assert.assertEquals(expressions.getCommitExpression().toString(),
                "READ PERMISSION WAS INVOKED ON PersistentResource { type=model, id=null }  "
                        + "FOR EXPRESSION [FIELDS(FAILURE) OR ENTITY(((user has all access PASSED)) "
                        + "AND ((user has no access FAILED)))]");

    }

    @Test
    public void testSpecificFieldExpressionText() {
        @Entity
        @Include
        @UpdatePermission(expression = "user has no access")
        class Model {
            @UpdatePermission(expression = "user has all access OR user has no access")
            public int foo;
        }

        dictionary.bindEntity(Model.class);

        PersistentResource resource = newResource(new Model(), Model.class);
        ChangeSpec changes = new ChangeSpec(resource, "foo", 1, 2);

        PermissionExpressionBuilder.Expressions expressions = builder.buildSpecificFieldExpressions(
                resource,
                UpdatePermission.class,
                "foo",
                changes);

        Assert.assertEquals(expressions.getCommitExpression().toString(),
                "UPDATE PERMISSION WAS INVOKED ON PersistentResource { type=model, id=null } WITH CHANGES ChangeSpec "
                        + "{ resource=PersistentResource { type=model, id=null }, field=foo, original=1, "
                        + "modified=2} FOR EXPRESSION [FIELD(((user has all access WAS UNEVALUATED)) OR ((user has no "
                        + "access WAS UNEVALUATED)))]");

        expressions.getCommitExpression().evaluate();

        Assert.assertEquals(expressions.getCommitExpression().toString(),
                "UPDATE PERMISSION WAS INVOKED ON PersistentResource { type=model, id=null } WITH CHANGES ChangeSpec "
                        + "{ resource=PersistentResource { type=model, id=null }, field=foo, original=1, modified=2} "
                        + "FOR EXPRESSION [FIELD(((user has all access PASSED)) OR ((user has no "
                        + "access WAS UNEVALUATED)))]");

    }

    public <T> PersistentResource newResource(T obj, Class<T> cls) {
        RequestScope requestScope = new RequestScope(null, null, null, dictionary, null, null);
        return new PersistentResource<>(obj, requestScope);
    }
}
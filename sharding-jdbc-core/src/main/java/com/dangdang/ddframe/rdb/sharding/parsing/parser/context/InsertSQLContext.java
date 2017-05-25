/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.parsing.parser.context;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.api.rule.TableRule;
import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expr.SQLExpr;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expr.SQLNumberExpr;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.expr.SQLPlaceholderExpr;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.AutoGeneratedKeysToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.ItemsToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.SQLToken;
import com.google.common.base.Optional;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Insert SQL上下文.
 *
 * @author zhangliang
 */
@Getter
@Setter
public final class InsertSQLContext extends AbstractSQLContext {
    
    private final Collection<ShardingColumnContext> shardingColumnContexts = new LinkedList<>();
    
    private GeneratedKeyContext generatedKeyContext;
    
    private int columnsListLastPosition;
    
    private int valuesListLastPosition;
    
    public InsertSQLContext() {
        super(SQLType.INSERT);
    }
    
    /**
     * 判断是否包含该列.
     *
     * @param columnName 列名称
     * @return 是否包含该列
     */
    public boolean hasColumn(final String columnName) {
        for (ShardingColumnContext shardingColumnContext : shardingColumnContexts) {
            if (shardingColumnContext.getColumnName().equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取自增主键.
     *
     * @param shardingRule 分片规则
     * @return 自增列与主键映射表
     */
    public Map<String, Number> generateKeys(final ShardingRule shardingRule) {
        if (null == generatedKeyContext) {
            return Collections.emptyMap();
        }
        Optional<TableRule> tableRuleOptional = shardingRule.tryFindTableRule(getTables().iterator().next().getName());
        if (!tableRuleOptional.isPresent()) {
            return Collections.emptyMap();
        }
        TableRule tableRule = tableRuleOptional.get();
        Map<String, Number> result = new LinkedHashMap<>(1, 1);
        Number generatedKey;
        if (null != tableRule.getIdGenerator()) {
            generatedKey = tableRule.getIdGenerator().generateId();
        } else if (null != shardingRule.getIdGenerator()) {
            generatedKey = shardingRule.getIdGenerator().generateId();
        } else {
            // TODO 使用default id生成器
            generatedKey = null;
        }
        result.put(generatedKeyContext.getColumn(), generatedKey);
        generatedKeyContext.getValues().add(generatedKey);
        return result;
    }
    
    /**
     * 追加自增主键标记对象.
     *
     * @param shardingRule 分片规则
     */
    public void appendGenerateKeysToken(final ShardingRule shardingRule) {
        Optional<AutoGeneratedKeysToken> autoGeneratedKeysToken =  findAutoGeneratedKeysToken();
        if (!autoGeneratedKeysToken.isPresent()) {
            return;
        }
        Map<String, Number> generatedKeys = generateKeys(shardingRule);
        String tableName = getTables().get(0).getName();
        ItemsToken valuesToken = new ItemsToken(autoGeneratedKeysToken.get().getBeginPosition());
        for (Map.Entry<String, Number> entry : generatedKeys.entrySet()) {
            valuesToken.getItems().add(entry.getValue().toString());
            addCondition(shardingRule, new ShardingColumnContext(entry.getKey(), tableName, true), new SQLNumberExpr(entry.getValue()));
        }
        getSqlTokens().remove(autoGeneratedKeysToken.get());
        getSqlTokens().add(valuesToken);
    }
    
    /**
     * 追加自增主键标记对象.
     *
     * @param shardingRule 分片规则
     * @param parametersSize 参数个数
     */
    public void appendGenerateKeysToken(final ShardingRule shardingRule, final int parametersSize) {
        if (null == generatedKeyContext) {
            return;
        }
        Optional<AutoGeneratedKeysToken> autoGeneratedKeysToken =  findAutoGeneratedKeysToken();
        if (!autoGeneratedKeysToken.isPresent()) {
            return;
        }
        String tableName = getTables().get(0).getName();
        ItemsToken valuesToken = new ItemsToken(autoGeneratedKeysToken.get().getBeginPosition());
        valuesToken.getItems().add("?");
        addCondition(shardingRule, new ShardingColumnContext(generatedKeyContext.getColumn(), tableName, true), new SQLPlaceholderExpr(parametersSize));
        getSqlTokens().remove(autoGeneratedKeysToken.get());
        getSqlTokens().add(valuesToken);
    }
    
    private Optional<AutoGeneratedKeysToken> findAutoGeneratedKeysToken() {
        for (SQLToken each : getSqlTokens()) {
            if (each instanceof AutoGeneratedKeysToken) {
                return Optional.of((AutoGeneratedKeysToken) each);
            }
        }
        return Optional.absent();
    }
    
    private void addCondition(final ShardingRule shardingRule, final ShardingColumnContext shardingColumnContext, final SQLExpr sqlExpr) {
        if (shardingRule.isShardingColumn(shardingColumnContext)) {
            getConditionContext().add(new ConditionContext.Condition(shardingColumnContext, sqlExpr));
        }
    }
}

/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.expression.subquery;

import com.foundationdb.qp.operator.Operator;
import com.foundationdb.qp.rowtype.RowType;
import com.foundationdb.server.expression.ExpressionEvaluation;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.ValueSource;
import com.foundationdb.server.types.util.BoolValueSource;

public final class ExistsSubqueryExpression extends SubqueryExpression {

    @Override
    public ExpressionEvaluation evaluation() {
        return new InnerEvaluation(subquery(), outerRowType(), innerRowType(),
                                   bindingPosition());
    }

    @Override
    public AkType valueType() {
        return AkType.BOOL;
    }

    @Override
    public String toString() {
        return "EXISTS(" + subquery() + ")";
    }

    public ExistsSubqueryExpression(Operator subquery, RowType outerRowType, 
                                    RowType innerRowType, int bindingPosition) {
        super(subquery, outerRowType, innerRowType, bindingPosition);
    }

    @Override
    public String name()
    {
        return "EXISTS";
    }
                                 
    private static final class InnerEvaluation extends SubqueryExpressionEvaluation {
        @Override
        public ValueSource doEval() {
            boolean empty = (next() == null);
            return BoolValueSource.of(!empty);
        }

        private InnerEvaluation(Operator subquery,
                                RowType outerRowType, RowType innerRowType, 
                                int bindingPosition) {
            super(subquery, outerRowType, innerRowType, bindingPosition);
        }
    }

}
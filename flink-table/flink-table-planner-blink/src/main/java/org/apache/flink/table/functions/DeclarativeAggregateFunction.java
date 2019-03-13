/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.functions;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.expressions.Expression;
import org.apache.flink.table.expressions.FieldReferenceExpression;
import org.apache.flink.util.Preconditions;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API for aggregation functions that are expressed in terms of expressions.
 *
 * <p>When implementing a new expression-based aggregate function, you should first decide how many
 * operands your function will have by implementing {@link #operandCount} method. And then you can
 * use {@link #operand} fields to represent your operand, like `operand(0)`, `operand(2)`.
 *
 * <p>Then you should declare all your buffer attributes by implementing
 * {@link #aggBufferAttributes}. You should declare all buffer attributes as
 * {@link FieldReferenceExpression}, and make sure the name of your attributes are unique within
 * the function and it should not conflict with operandIndex. You can then use these attributes when
 * defining {@link #initialValuesExpressions}, {@link #accumulateExpressions},
 * {@link #mergeExpressions} and {@link #getValueExpression}.
 *
 * <p>See an full example: {@link AvgAggFunction}.
 */
public abstract class DeclarativeAggregateFunction extends UserDefinedFunction {

	private transient Set<String> aggBufferNamesCache;

	/**
	 * How many operands your function will deal with.
	 */
	public abstract int operandCount();

	/**
	 * All fields of the aggregate buffer.
	 */
	public abstract FieldReferenceExpression[] aggBufferAttributes();

	/**
	 * The result type of the function.
	 */
	public abstract TypeInformation getResultType();

	/**
	 * Expressions for initializing empty aggregation buffers.
	 */
	public abstract Expression[] initialValuesExpressions();

	/**
	 * Expressions for accumulating the mutable aggregation buffer based on an input row.
	 */
	public abstract Expression[] accumulateExpressions();

	/**
	 * Expressions for retracting the mutable aggregation buffer based on an input row.
	 */
	public abstract Expression[] retractExpressions();

	/**
	 * A sequence of expressions for merging two aggregation buffers together. When defining these
	 * expressions, you can use the syntax {@code attributeName} and
	 * {@code mergeOperand(attributeName)} to refer to the attributes corresponding to each of
	 * the buffers being merged.
	 */
	public abstract Expression[] mergeExpressions();

	/**
	 * An expression which returns the final value for this aggregate function.
	 */
	public abstract Expression getValueExpression();

	private Set<String> getAggBufferNames() {
		if (aggBufferNamesCache == null) {
			aggBufferNamesCache = Arrays.stream(aggBufferAttributes())
					.map(FieldReferenceExpression::getName)
					.collect(Collectors.toSet());
		}
		return aggBufferNamesCache;
	}

	private void validateOperandName(String name) {
		if (getAggBufferNames().contains(name)) {
			throw new IllegalStateException(
					String.format("Agg buffer name(%s) should not same to operands.", name));
		}
	}

	/**
	 * Args of accumulate and retract, the input value (usually obtained from a new arrived data).
	 */
	public final FieldReferenceExpression[] operands() {
		int operandCount = operandCount();
		Preconditions.checkState(operandCount >= 0, "inputCount must be greater than or equal to 0.");
		FieldReferenceExpression[] ret = new FieldReferenceExpression[operandCount];
		for (int i = 0; i < operandCount; i++) {
			String name = String.valueOf(i);
			validateOperandName(name);
			ret[i] = new FieldReferenceExpression(name);
		}
		return ret;
	}

	/**
	 * Arg of accumulate and retract, the input value (usually obtained from a new arrived data).
	 */
	public final FieldReferenceExpression operand(int i) {
		String name = String.valueOf(i);
		if (getAggBufferNames().contains(name)) {
			throw new IllegalStateException(
					String.format("Agg buffer name(%s) should not same to operands.", name));
		}
		return new FieldReferenceExpression(name);
	}

	/**
	 * Merge input of {@link #mergeExpressions()}, the input are AGG buffer generated by user definition.
	 */
	public final FieldReferenceExpression mergeOperand(FieldReferenceExpression aggBuffer) {
		String name = String.valueOf(Arrays.asList(aggBufferAttributes()).indexOf(aggBuffer));
		validateOperandName(name);
		return new FieldReferenceExpression(name);
	}

	/**
	 * Merge inputs of {@link #mergeExpressions()}, these inputs are agg buffer generated by user definition.
	 */
	public final FieldReferenceExpression[] mergeOperands() {
		FieldReferenceExpression[] aggBuffers = aggBufferAttributes();
		FieldReferenceExpression[] ret = new FieldReferenceExpression[aggBuffers.length];
		for (int i = 0; i < aggBuffers.length; i++) {
			String name = String.valueOf(i);
			validateOperandName(name);
			ret[i] = new FieldReferenceExpression(name);
		}
		return ret;
	}
}
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
package org.apache.flink.table.planner.plan.rules.logical

import org.apache.flink.api.scala._
import org.apache.flink.table.api._
import org.apache.flink.table.planner.plan.nodes.FlinkConventions
import org.apache.flink.table.planner.plan.optimize.program._
import org.apache.flink.table.planner.plan.rules.FlinkBatchRuleSets
import org.apache.flink.table.planner.runtime.utils.JavaUserDefinedScalarFunctions.PythonScalarFunction
import org.apache.flink.table.planner.utils.{TableFunc2, TableTestBase}

import org.apache.calcite.plan.hep.HepMatchOrder
import org.junit.jupiter.api.{BeforeEach, Test}

/** Test for [[SplitPythonConditionFromCorrelateRule]]. */
class SplitPythonConditionFromCorrelateRuleTest extends TableTestBase {

  private val util = batchTestUtil()

  @BeforeEach
  def setup(): Unit = {
    val programs = new FlinkChainedProgram[BatchOptimizeContext]()
    // query decorrelation
    programs.addLast("decorrelate", new FlinkDecorrelateProgram)
    programs.addLast(
      "logical",
      FlinkVolcanoProgramBuilder.newBuilder
        .add(FlinkBatchRuleSets.LOGICAL_OPT_RULES)
        .setRequiredOutputTraits(Array(FlinkConventions.LOGICAL))
        .build()
    )
    programs.addLast(
      "logical_rewrite",
      FlinkHepRuleSetProgramBuilder.newBuilder
        .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
        .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
        .add(FlinkBatchRuleSets.LOGICAL_REWRITE)
        .build()
    )
    util.replaceBatchProgram(programs)

    util.addTableSource[(Int, Int, String)]("MyTable", 'a, 'b, 'c)
    util.addFunction("func", new TableFunc2)
    util.addFunction("pyFunc", new PythonScalarFunction("pyFunc"))
  }

  @Test
  def testPythonFunctionInCorrelateCondition(): Unit = {
    val sqlQuery = "SELECT a, b, c, s, l FROM MyTable, LATERAL TABLE(func(c)) AS T(s, l) " +
      "WHERE l = a and c = s and pyFunc(l, l) = 2 and l + 1 = l * l"
    util.verifyRelPlan(sqlQuery)
  }
}

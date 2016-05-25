/*
 * This file is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tresamigos.smv

import org.apache.spark.sql._, types._, functions._

class SelectWithReplaceTest extends SmvTestUtil {
  val fields = Seq("name:String", "friends:Integer")
  val schema = fields.mkString(";")
  val data = Seq("Adam,1", "Beth,2", "Caleb,3", "David,4")

  def testDf(sqlContext: SQLContext): DataFrame =
    createSchemaRdd(schema, data.mkString(";"))

  test("should add new columns without modification") {
    val input = testDf(sqlContext)
    val res = input.selectWithReplace(input("friends") + 1 as "newfriends")
    assertSrddSchemaEqual(res, schema + ";newfriends:Integer")
    assertSrddDataEqual(res,
      "Adam,1,2;Beth,2,3;Caleb,3,4;David,4,5")
  }

  test("should overwrite existing column with the same name") {
    val input = testDf(sqlContext)
    val res = input.selectWithReplace(input("friends") + 1 as "friends")
    assertSrddSchemaEqual(res, "name:String;friends:Integer")
    assertSrddDataEqual(res,
      "Adam,2;Beth,3;Caleb,4;David,5")
  }

  test("should accept a column aliased multiple times") {
    val input = testDf(sqlContext)
    val res = input.selectWithReplace(input("friends") as "friends" as "friends")
    assertSrddSchemaEqual(res, schema)
    assertSrddDataEqual(res, data.mkString(";"))
  }
}

class SelectPlusMinusTest extends SmvTestUtil {
  test("test SelectPlus") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("a:Double;b:Double", "1.0,10.0;2.0,20.0;3.0,30.0")
    val res = df.selectPlus('b + 2.0 as 'bplus2)
    assertSrddDataEqual(res,
      "1.0,10.0,12.0;" +
      "2.0,20.0,22.0;" +
      "3.0,30.0,32.0")
  }

  test("test SelectPlusPrefix") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("a:Double;b:Double", "1.0,10.0;2.0,20.0;3.0,30.0")
    val res = df.selectPlusPrefix('b + 2.0 as 'bplus2)
    assertSrddDataEqual(res,
      "12.0,1.0,10.0;" +
      "22.0,2.0,20.0;" +
      "32.0,3.0,30.0")
  }

  test("test SelectMinus") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("a:Double;b:Double", "1.0,10.0;2.0,20.0;3.0,30.0")
    val res = df.selectMinus("b")
    assertSrddDataEqual(res,
      "1.0;" +
      "2.0;" +
      "3.0")
  }

  test("test selectExpandStruct") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("id:String;a:Double;b:Double", "a,1.0,10.0;a,2.0,20.0;b,3.0,30.0")
    val dfStruct = df.select($"id", struct("a", "b") as "c")
    val res = dfStruct.selectExpandStruct("c")
    assertSrddDataEqual(res, "a,1.0,10.0;a,2.0,20.0;b,3.0,30.0")
  }
}

class renameFieldTest extends SmvTestUtil {
  test("test rename fields") {
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      "1,2.0,hello")

    val result = df.renameField("a" -> "aa", "c" -> "cc")

    val fieldNames = result.schema.fieldNames
    assert(fieldNames === Seq("aa", "b", "cc"))
    assert(result.collect.map(_.toString) === Seq("[1,2.0,hello]") )
  }

  test("test rename to existing field") {
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      "1,2.0,hello")

    val e = intercept[IllegalArgumentException] {
      val result = df.renameField("a" -> "c")
    }
    assert(e.getMessage === "Rename to existing fields: c")
  }

  test("test prefixing field names") {
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      "1,2.0,hello")

    val result = df.prefixFieldNames("xx_")

    val fieldNames = result.schema.fieldNames
    assert(fieldNames === Seq("xx_a", "xx_b", "xx_c"))
    assert(result.collect.map(_.toString) === Seq("[1,2.0,hello]") )
  }

  test("test postfixing field names") {
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      "1,2.0,hello")

    val result = df.postfixFieldNames("_xx")

    val fieldNames = result.schema.fieldNames
    assert(fieldNames === Seq("a_xx", "b_xx", "c_xx"))
    assert(result.collect.map(_.toString) === Seq("[1,2.0,hello]") )
  }

  test("rename field should preserve metadata in renamed fields") {
    val df = createSchemaRdd("a:Integer; b:String", "1,abc;1,def;2,ghij")
    val desc = "c description"
    val res1 = df.groupBy(df("a")).agg(functions.count(df("a")) withDesc desc as "c")
    res1.smvGetDesc() shouldBe Seq(("a" -> ""), ("c" -> desc))

    val res2 = res1.renameField("c" -> "d")
    res2.smvGetDesc() shouldBe Seq(("a" -> ""), ("d" -> desc))
  }

  test("rename field should preserve metadata for unrenamed fields") {
    val df = createSchemaRdd("a:Integer; b:String", "1,abc;1,def;2,ghij")
    val desc = "c description"
    val res1 = df.groupBy(df("a")).agg(functions.count(df("a")) withDesc desc as "c")
    res1.smvGetDesc() shouldBe Seq(("a" -> ""), ("c" -> desc))

    val res2 = res1.renameField("a" -> "d")
    res2.smvGetDesc() shouldBe Seq(("d" -> ""), ("c" -> desc))
  }
}

class JoinHelperTest extends SmvTestUtil {
  test("test joinUniqFieldNames") {
    val ssc = sqlContext; import ssc.implicits._
    val srdd1 = createSchemaRdd("a:Integer; b:Double; c:String",
      """1,2.0,hello;
         1,3.0,hello;
         2,10.0,hello2;
         2,11.0,hello3"""
    )

    val srdd2 = createSchemaRdd("a2:Integer; c:String",
      """1,asdf;
         2,asdfg"""
    )

    val result = srdd1.joinUniqFieldNames(srdd2, $"a" === $"a2", "inner")
    val fieldNames = result.columns
    assert(fieldNames === Seq("a", "b", "c", "a2", "_c"))
    assertUnorderedSeqEqual(result.collect.map(_.toString), Seq(
    "[1,2.0,hello,1,asdf]",
    "[1,3.0,hello,1,asdf]",
    "[2,10.0,hello2,2,asdfg]",
    "[2,11.0,hello3,2,asdfg]"))
  }

  test("test joinUniqFieldNames with ignore case") {
    val ssc = sqlContext; import ssc.implicits._
    val srdd1 = createSchemaRdd("a:Integer; b:Double; C:String",
      """1,2.0,hello;
      2,11.0,hello3"""
    )

    val srdd2 = createSchemaRdd("a2:Integer; c:String",
      """1,asdf;
      2,asdfg"""
    )

    val result = srdd1.joinUniqFieldNames(srdd2, $"a" === $"a2", "inner")
    assertSrddSchemaEqual(result, "a: Integer; b: Double; C: String; a2: Integer; _c: String")
  }

  test("test joinByKey") {
    val ssc = sqlContext; import ssc.implicits._
    val srdd1 = createSchemaRdd("a:Integer; b:Double; c:String",
      """1,2.0,hello;
         1,3.0,hello;
         2,10.0,hello2;
         2,11.0,hello3"""
    )

    val srdd2 = createSchemaRdd("a:Integer; c:String",
      """1,asdf;
         2,asdfg"""
    )

    val result = srdd1.joinByKey(srdd2, Seq("a"), "inner")
    val fieldNames = result.columns
    assert(fieldNames === Seq("a", "b", "c", "_c"))
    assertUnorderedSeqEqual(result.collect.map(_.toString), Seq(
    "[1,2.0,hello,asdf]",
    "[1,3.0,hello,asdf]",
    "[2,10.0,hello2,asdfg]",
    "[2,11.0,hello3,asdfg]"))
  }

  test("outer joinByKey with single key column") {
    val df1 = createSchemaRdd("a:Integer;b:String", """1,x1;2,y1;3,z1""")
    val df2 = createSchemaRdd("a:Integer;b:String", """1,x2;4,w2;""")
    val res = df1.joinByKey(df2, Seq("a"), SmvJoinType.Outer)
    assert(res.columns === Seq("a", "b", "_b"))
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
    "[1,x1,x2]",
    "[2,y1,null]",
    "[3,z1,null]",
    "[4,null,w2]"))
  }

  test("outer joinByKey with multiple key columns") {
    val df1 = createSchemaRdd("k1:Integer;k2:Integer;a:String", "1,1,x1;1,2,x2;2,1,x3;2,2,x4")
    val df2 = createSchemaRdd("k1:Integer;k2:Integer;b:String", "1,1,y1;1,3,y2;3,1,y3;3,3,y4")
    val res = df1.joinByKey(df2, Seq("k1", "k2"), SmvJoinType.Outer)
    assert(res.columns === Seq("k1", "k2", "a", "b"))
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,1,x1,y1]",
      "[1,2,x2,null]",
      "[1,3,null,y2]",
      "[2,1,x3,null]",
      "[2,2,x4,null]",
      "[3,1,null,y3]",
      "[3,3,null,y4]"))
  }

  test("joinMultipleByKey") {
    val df1 = createSchemaRdd("a:Integer;b:String", """1,x1;2,y1;3,z1""")
    val df2 = createSchemaRdd("a:Integer;b:String", """1,x1;4,w2;""")
    val df3 = createSchemaRdd("a:Integer;b:String", """1,x3;5,w3;""")

    val mtjoin = df1.joinMultipleByKey(Seq("a"), SmvJoinType.Inner).
      joinWith(df2, "_df2").
      joinWith(df3, "_df3", SmvJoinType.Outer)

    val res = mtjoin.doJoin()

    assert(res.columns === Seq("a", "b", "b_df2", "b_df3"))
    assertSrddDataEqual(res,
      """1,x1,x1,x3;
        |5,null,null,w3""".stripMargin
    )

    val res2 = mtjoin.doJoin(true)
    assertSrddDataEqual(res2,
      """1,x1;
        |5,null""".stripMargin
    )
  }
}

class dedupByKeyTest extends SmvTestUtil {
  test("test dedupByKey") {
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      """1,2.0,hello;
         1,3.0,hello;
         2,10.0,hello2;
         2,11.0,hello3"""
    )

    val result1 = df.dedupByKey("a")
    assertUnorderedSeqEqual(result1.collect.map(_.toString), Seq(
      "[1,2.0,hello]",
      "[2,10.0,hello2]" ))

    val fieldNames1 = result1.schema.fieldNames
    assert(fieldNames1 === Seq("a", "b", "c"))

    val result2 = df.dedupByKey("a", "c")
    assertUnorderedSeqEqual(result2.collect.map(_.toString), Seq(
    "[1,2.0,hello]",
    "[2,10.0,hello2]",
    "[2,11.0,hello3]" ))

    val fieldNames2 = result2.schema.fieldNames
    assert(fieldNames2 === Seq("a", "b", "c"))

  }

  test("test dedupByKey with nulls") {
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      """1,,hello;
         1,3.0,hello1;
         2,10.0,;
         2,11.0,hello3"""
    )

    val res = df.dedupByKey("a")
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,null,hello]",
      "[2,10.0,null]" ))
  }

  test("test dedupByKeyWithOrder") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("a:Integer; b:Double; c:String",
      """1,,hello;
         1,3.0,hello1;
         2,11.0,;
         2,10.0,hello3"""
    )

    val res = df.dedupByKeyWithOrder($"a")($"b".desc)
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,3.0,hello1]",
      "[2,11.0,null]" ))
  }

  test("test dedupByKeyWithOrder with timestamp") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("a:Integer; b:Timestamp[yyyyMMdd]",
      """1,20150102;
         1,20140108;
         2,20130712;
         2,20150504"""
    )

    val res = df.dedupByKeyWithOrder("a")($"b".desc)
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,2015-01-02 00:00:00.0]",
      "[2,2015-05-04 00:00:00.0]"
    ))
  }
}

class smvOverlapCheckTest extends SmvTestUtil {
  test("test smvOverlapCheck") {
    val s1 = createSchemaRdd("k: String", "a;b;c")
    val s2 = createSchemaRdd("k: String", "a;b;c;d")
    val s3 = createSchemaRdd("k: String", "c;d")

    val res = s1.smvOverlapCheck("k")(s2, s3)
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[a,110]",
      "[b,110]",
      "[c,111]",
      "[d,011]"))

  }
}

class smvHashSampleTest extends SmvTestUtil {
  test("test smvHashSample") {
    val ssc = sqlContext; import ssc.implicits._
    val a = createSchemaRdd("key:String", "a;b;c;d;e;f;g;h;i;j;k")
    val res = a.unionAll(a).smvHashSample($"key", 0.3)
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[a]",
      "[g]",
      "[i]",
      "[a]",
      "[g]",
      "[i]"))
  }
}

class smvCoalesceTest extends SmvTestUtil {
  test("Test smvCoalesce") {
    val ssc = sqlContext; import ssc.implicits._
    val a = createSchemaRdd("key:String", "a;b;c;d;e;f;g;h;i;j;k")
    val res = a.coalesce(1)
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[a]",
      "[b]",
      "[c]",
      "[d]",
      "[e]",
      "[f]",
      "[g]",
      "[h]",
      "[i]",
      "[j]",
      "[k]"))
    assert(res.rdd.partitions.size === 1)
  }
}


class smvPipeCount extends SmvTestUtil {
  test("Test smvPipeCount") {
    val ssc = sqlContext; import ssc.implicits._
    val a = createSchemaRdd("key:String", "a;b;c;d;e;f;g;h;i;j;k")
    val counter = sc.accumulator(0l)

    val n1 = a.smvPipeCount(counter).count
    val n2 = counter.value

    assert(n1 === n2)
  }
}

class OtherDFHelperTest extends SmvTestUtil {
  test("Test smvDiscoverPK") {
    val a = createSchemaRdd("a:String; b:String; c:String",
      """1,2,1;
         1,1,2;
         2,1,2;
         2,2,2""")
    val (res, cnt) = a.smvDiscoverPK()

    assertUnorderedSeqEqual(res, Seq("a", "b"))
    assert(cnt === 4)
  }
}

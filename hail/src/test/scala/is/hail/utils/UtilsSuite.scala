package is.hail.utils

import is.hail.HailSuite
import is.hail.check.{Gen, Prop}
import is.hail.io.fs.HadoopFS
import org.apache.spark.storage.StorageLevel
import org.testng.annotations.Test

class UtilsSuite extends HailSuite {
  @Test def testD_==() {
    assert(D_==(1, 1))
    assert(D_==(1, 1 + 1E-7))
    assert(!D_==(1, 1 + 1E-5))
    assert(D_==(1E10, 1E10 + 1))
    assert(!D_==(1E-10, 2E-10))
    assert(D_==(0.0, 0.0))
    assert(D_!=(1E-307, 0.0))
    assert(D_==(1E-308, 0.0))
    assert(D_==(1E-320, -1E-320))
  }

  @Test def testFlushDouble() {
    assert(flushDouble(8.0E-308) == 8.0E-308)
    assert(flushDouble(-8.0E-308) == -8.0E-308)
    assert(flushDouble(8.0E-309) == 0.0)
    assert(flushDouble(-8.0E-309) == 0.0)
    assert(flushDouble(0.0) == 0.0)
  }

  @Test def testAreDistinct() {
    assert(Array().areDistinct())
    assert(Array(1).areDistinct())
    assert(Array(1, 2).areDistinct())
    assert(!Array(1, 1).areDistinct())
    assert(!Array(1, 2, 1).areDistinct())
  }

  @Test def testIsIncreasing() {
    assert(Seq[Int]().isIncreasing)
    assert(Seq(1).isIncreasing)
    assert(Seq(1, 2).isIncreasing)
    assert(!Seq(1, 1).isIncreasing)
    assert(!Seq(1, 2, 1).isIncreasing)

    assert(Array(1, 2).isIncreasing)
  }

  @Test def testIsSorted() {
    assert(Seq[Int]().isSorted)
    assert(Seq(1).isSorted)
    assert(Seq(1, 2).isSorted)
    assert(Seq(1, 1).isSorted)
    assert(!Seq(1, 2, 1).isSorted)

    assert(Array(1, 1).isSorted)
  }

  @Test def testHadoopStripCodec() {
    assert(sFS.stripCodec("file.tsv") == "file.tsv")
    assert(sFS.stripCodec("file.tsv.gz") == "file.tsv")
    assert(sFS.stripCodec("file.tsv.bgz") == "file.tsv")
    assert(sFS.stripCodec("file.tsv.lz4") == "file.tsv")
    assert(sFS.stripCodec("file") == "file")
  }

  @Test def testPairRDDNoDup() {
    val answer1 = Array((1, (1, Option(1))), (2, (4, Option(2))), (3, (9, Option(3))), (4, (16, Option(4))))
    val pairRDD1 = sc.parallelize(Array(1, 2, 3, 4)).map { i => (i, i * i) }
    val pairRDD2 = sc.parallelize(Array(1, 2, 3, 4, 1, 2, 3, 4)).map { i => (i, i) }
    val join = pairRDD1.leftOuterJoin(pairRDD2.distinct)

    assert(join.collect().sortBy(t => t._1) sameElements answer1)
    assert(join.count() == 4)
  }

  @Test def testForallExists() {
    val rdd1 = sc.parallelize(Array(1, 2, 3, 4, 5))

    assert(rdd1.forall(_ > 0))
    assert(!rdd1.forall(_ <= 0))
    assert(!rdd1.forall(_ < 3))
    assert(rdd1.exists(_ > 4))
    assert(!rdd1.exists(_ < 0))
  }

  @Test def testSortFileStatus() {
    val fs = new HadoopFS(new SerializableHadoopConfiguration(sc.hadoopConfiguration))

    val partFileNames = fs.glob("src/test/resources/part-*").sortBy(fileSystem => getPartNumber(fileSystem.getPath.getName)).map(_.getPath.getName)

    assert(partFileNames(0) == "part-40001" && partFileNames(1) == "part-100001")
  }

  @Test def storageLevelStringTest() = {
    val sls = List(
      "NONE", "DISK_ONLY", "DISK_ONLY_2", "MEMORY_ONLY", "MEMORY_ONLY_2", "MEMORY_ONLY_SER", "MEMORY_ONLY_SER_2",
      "MEMORY_AND_DISK", "MEMORY_AND_DISK_2", "MEMORY_AND_DISK_SER", "MEMORY_AND_DISK_SER_2", "OFF_HEAP")

    sls.foreach { sl => StorageLevel.fromString(sl) }
  }

  @Test def testDictionaryOrdering() {
    val stringList = Seq("Cats", "Crayon", "Dog")

    val longestToShortestLength = Ordering.by[String, Int](-_.length)
    val byFirstLetter = Ordering.by[String, Char](_.charAt(0))
    val alphabetically = Ordering.String

    val ord1 = dictionaryOrdering(alphabetically, byFirstLetter, longestToShortestLength)
    assert(stringList.sorted(ord1) == stringList)
    val ord2 = dictionaryOrdering(byFirstLetter, longestToShortestLength)
    assert(stringList.sorted(ord2) == Seq("Crayon", "Cats", "Dog"))
  }

  @Test def testCollectAsSet() {
    Prop.forAll(Gen.buildableOf[Array](Gen.choose(-1000, 1000)), Gen.choose(1, 10)) { case (values, parts) =>
      val rdd = sc.parallelize(values, numSlices = parts)
      rdd.collectAsSet() == rdd.collect().toSet
    }.check()
  }

  @Test def testDigitsNeeded() {
    assert(digitsNeeded(0) == 1)
    assert(digitsNeeded(1) == 1)
    assert(digitsNeeded(7) == 1)
    assert(digitsNeeded(9) == 1)
    assert(digitsNeeded(13) == 2)
    assert(digitsNeeded(30173) == 5)
  }

  @Test def testMangle() {
    val c1 = Array("a", "b", "c", "a", "a", "c", "a")
    val (c2, diff) = mangle(c1)
    assert(c2.toSeq == Seq("a", "b", "c", "a_1", "a_2", "c_1", "a_3"))
    assert(diff.toSeq == Seq("a" -> "a_1", "a" -> "a_2", "c" -> "c_1", "a" -> "a_3"))

    val c3 = Array("a", "b", "c", "a", "a", "c", "a")
    val (c4, diff2) = mangle(c1, "D" * _)
    assert(c4.toSeq == Seq("a", "b", "c", "aD", "aDD", "cD", "aDDD"))
    assert(diff2.toSeq == Seq("a" -> "aD", "a" -> "aDD", "c" -> "cD", "a" -> "aDDD"))
  }

  @Test def toMapUniqueEmpty() {
    assert(toMapIfUnique(Seq[(Int, Int)]())(x => x % 2) == Right(Map()))
  }

  @Test def toMapUniqueSingleton() {
    assert(toMapIfUnique(Seq(1 -> 2))(x => x % 2) == Right(Map(1 -> 2)))
  }

  @Test def toMapUniqueSmallNoDupe() {
    assert(toMapIfUnique(Seq(1 -> 2, 3 -> 6, 10 -> 2))(x => x % 5) ==
      Right(Map(1 -> 2, 3 -> 6, 0 -> 2)))
  }

  @Test def toMapUniqueSmallDupes() {
    assert(toMapIfUnique(Seq(1 -> 2, 6 -> 6, 10 -> 2))(x => x % 5) ==
      Left(Map(1 -> Seq(1, 6))))
  }

  @Test def testItemPartition(): Unit = {
    def test(n: Int, k: Int) {
      val a = new Array[Int](k)
      var prevj = 0
      for (i <- 0 until n) {
        val j = itemPartition(i, n, k)

        assert(j >= 0)
        assert(j < k)
        a(j) += 1

        assert(prevj <= j)
        prevj = j
      }
      val p = partition(n, k)
      assert(a sameElements p)
    }

    test(0, 0)
    test(0, 4)
    test(2, 4)
    test(2, 5)
    test(12, 4)
    test(12, 5)
  }
}

package is.hail.utils.richUtils

import java.io._

import is.hail.HailContext
import is.hail.io.fs.FS
import is.hail.io.index.IndexWriter
import is.hail.rvd.RVDContext
import is.hail.utils._
import is.hail.sparkextras._
import org.apache.hadoop.conf.{Configuration => HadoopConf}
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

class RichContextRDD[T: ClassTag](crdd: ContextRDD[RVDContext, T]) {
  // Only use on CRDD's whose T is not dependent on the context
  def clearingRun: RDD[T] =
    crdd.cmap { (ctx, v) =>
      ctx.region.clear()
      v
    }.run

  // If idxPath is null, then mkIdxWriter should return null and not read its string argument
  def writePartitions(
    path: String,
    idxRelPath: String,
    stageLocally: Boolean,
    mkIdxWriter: (FS, String) => IndexWriter,
    write: (RVDContext, Iterator[T], OutputStream, IndexWriter) => Long
  ): (Array[String], Array[Long]) = {
    val hc = HailContext.get
    val fs = hc.sFS
    val bcFS = hc.bcFS

    fs.mkDir(path + "/parts")
    if (idxRelPath != null)
      fs.mkDir(path + "/" + idxRelPath)

    val nPartitions = crdd.getNumPartitions

    val d = digitsNeeded(nPartitions)

    val (partFiles, partitionCounts) = crdd.cmapPartitionsWithIndex { (i, ctx, it) =>
      val fs = bcFS.value
      val f = partFile(d, i, TaskContext.get)
      val finalFilename = path + "/parts/" + f
      val finalIdxFilename = if (idxRelPath != null) path + "/" + idxRelPath + "/" + f + ".idx" else null
      val (filename, idxFilename) =
        if (stageLocally) {
          val context = TaskContext.get
          val partPath = fs.getTemporaryFile("file:///tmp")
          val idxPath = partPath + ".idx"
          context.addTaskCompletionListener { (context: TaskContext) =>
            fs.delete(partPath, recursive = false)
            fs.delete(idxPath, recursive = true)
          }
          partPath -> idxPath
        } else
          finalFilename -> finalIdxFilename
      val os = fs.unsafeWriter(filename)
      val iw = mkIdxWriter(fs, idxFilename)
      val count = write(ctx, it, os, iw)
      if (iw != null)
        iw.close()
      if (stageLocally) {
        fs.copy(filename, finalFilename)
        if (iw != null) {
          fs.copy(idxFilename + "/index", finalIdxFilename + "/index")
          fs.copy(idxFilename + "/metadata.json.gz", finalIdxFilename + "/metadata.json.gz")
        }
      }
      ctx.region.clear()
      Iterator.single(f -> count)
    }
      .collect()
      .unzip

    val itemCount = partitionCounts.sum
    assert(nPartitions == partitionCounts.length)

    (partFiles, partitionCounts)
  }
}

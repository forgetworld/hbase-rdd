package unicredit.spark

import org.apache.hadoop.hbase.client.{ Put, HBaseAdmin }
import org.apache.hadoop.hbase.{ HBaseConfiguration, HTableDescriptor, HColumnDescriptor, TableName }
import org.apache.hadoop.hbase.mapred.TableOutputFormat
import org.apache.hadoop.hbase.io.ImmutableBytesWritable

import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.hbase.util.Bytes

import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._

trait HBaseWriteSupport {
  implicit def toHBaseRDD(rdd: RDD[(String, Map[String, String])]): HBaseRDD = new HBaseRDD(rdd)
}

final class HBaseRDD(val rdd: RDD[(String, Map[String, String])]) extends Serializable {
  def convert(id: String, values: Map[String, String], family: String): (ImmutableBytesWritable, Put) = {
    implicit def bitify(s: String): Array[Byte] = Bytes.toBytes(s)

    val put = new Put(id)
    for ((key, value) <- values) {
      put.add(family, key, value)
    }
    (new ImmutableBytesWritable, put)
  }

  def createTable(table: String, family: String, admin: HBaseAdmin): Unit = {
    if (!admin.isTableAvailable(table)) {
      val tableName = TableName.valueOf(table)
      val tableDescriptor = new HTableDescriptor(tableName)

      tableDescriptor.addFamily(new HColumnDescriptor(family))
      admin.createTable(tableDescriptor)
    }
  }

  def toHBase(table: String, family: String)(implicit config: HBaseConfig): Unit = {
    val conf = HBaseConfiguration.create()

    conf.setBoolean("hbase.cluster.distributed", true)
    conf.setInt("hbase.client.scanner.caching", 10000)
    conf.set(TableOutputFormat.OUTPUT_TABLE, table)
    config(conf)

    createTable(table, family, new HBaseAdmin(conf))

    val jobConf = new JobConf(conf, getClass)
    jobConf.setOutputFormat(classOf[TableOutputFormat])

    rdd.map({ case (k, v) => convert(k, v, family) }).saveAsHadoopDataset(jobConf)
  }
}
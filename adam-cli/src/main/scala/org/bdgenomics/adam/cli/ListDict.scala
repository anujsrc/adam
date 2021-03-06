/**
 * Copyright 2013 Genome Bridge LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.adam.cli

import org.bdgenomics.adam.util.ParquetLogger
import org.bdgenomics.adam.rdd.ADAMContext._
import org.kohsuke.args4j.Argument
import org.apache.spark.SparkContext
import org.apache.hadoop.mapreduce.Job
import java.util.logging.Level
import org.bdgenomics.adam.avro.ADAMRecord
import org.bdgenomics.adam.models.SequenceRecord

object ListDict extends ADAMCommandCompanion {
  val commandName: String = "listdict"
  val commandDescription: String = "Print the contents of an ADAM sequence dictionary"

  def apply(cmdLine: Array[String]): ADAMCommand = {
    new ListDict(Args4j[ListDictArgs](cmdLine))
  }
}

class ListDictArgs extends Args4jBase with SparkArgs with ParquetArgs {
  @Argument(required = true, metaVar = "INPUT", usage = "The ADAM sequence dictionary to print", index = 0)
  val inputPath: String = null
}

class ListDict(protected val args: ListDictArgs) extends ADAMSparkCommand[ListDictArgs] {
  val companion: ADAMCommandCompanion = ListDict

  def run(sc: SparkContext, job: Job): Unit = {
    // Quiet parquet logging...
    ParquetLogger.hadoopLoggerLevel(Level.SEVERE)

    val dict = sc.adamDictionaryLoad[ADAMRecord](args.inputPath)

    dict.recordsIn.sortBy(_.id).foreach {
      rec: SequenceRecord =>
        println("%d\t%s\t%d".format(rec.id, rec.name, rec.length))
    }
  }

}

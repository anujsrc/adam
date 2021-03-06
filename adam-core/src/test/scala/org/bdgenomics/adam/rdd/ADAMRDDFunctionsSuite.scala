/**
 * Copyright 2013-2014. Genome Bridge LLC
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
package org.bdgenomics.adam.rdd

import org.bdgenomics.adam.avro.{
  ADAMContig,
  ADAMGenotype,
  ADAMNucleotideContigFragment,
  ADAMPileup,
  ADAMVariant,
  ADAMRecord,
  Base
}
import org.bdgenomics.adam.models.{
  ADAMVariantContext,
  ReferenceRegion,
  SequenceDictionary,
  SequenceRecord
}
import org.bdgenomics.adam.util.SparkFunSuite
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.variation.ADAMVariantContextRDDFunctions
import org.bdgenomics.adam.rdd.variation.ADAMVariationContext._
import org.apache.spark.rdd.RDD
import scala.util.Random

class ADAMRDDFunctionsSuite extends SparkFunSuite {

  sparkTest("can convert pileups to rods, bases at different pos, same reference") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(0L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.C)
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val rods = pileups.adamPileupsToRods(1)

    assert(rods.count === 2)
    assert(rods.filter(_.position.pos == 0L).count === 1)
    assert(rods.filter(_.position.pos == 0L).flatMap(_.pileups).filter(_.getReadBase == Base.A).count === 1)
    assert(rods.filter(_.position.pos == 1L).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.C).count === 1)
  }

  sparkTest("can convert pileups to rods, bases at same pos, different reference") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(0L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(0L)
      .setReferenceId(1)
      .setReadBase(Base.C)
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val rods = pileups.adamPileupsToRods(1)

    assert(rods.count === 2)
    assert(rods.filter(_.position.refId == 0).count === 1)
    assert(rods.filter(_.position.refId == 0).flatMap(_.pileups).filter(_.getReadBase == Base.A).count === 1)
    assert(rods.filter(_.position.refId == 1).count === 1)
    assert(rods.filter(_.position.refId == 1).flatMap(_.pileups).filter(_.getReadBase == Base.C).count === 1)
  }

  sparkTest("can convert pileups to rods, bases at same pos") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.C)
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val rods = pileups.adamPileupsToRods(1)

    assert(rods.count === 1)
    assert(rods.filter(_.position.pos == 1L).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).count === 2)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.A).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.C).count === 1)
  }

  sparkTest("can convert pileups to rods, bases at same pos, split by different sample") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .setRecordGroupSample("0")
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.C)
      .setRecordGroupSample("1")
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val rods = pileups.adamPileupsToRods(1)

    assert(rods.count === 1)
    assert(rods.filter(_.position.pos == 1L).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).count === 2)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.A).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.C).count === 1)
    assert(rods.filter(_.isSingleSample).count === 0)

    val split = rods.adamSplitRodsBySamples()

    assert(split.count === 2)
    assert(split.filter(_.position.pos == 1L).count === 2)
    assert(split.filter(_.isSingleSample).count === 2)
  }

  sparkTest("can convert pileups to rods, bases at same pos, split by same sample") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .setRecordGroupSample("1")
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.C)
      .setRecordGroupSample("1")
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val rods = pileups.adamPileupsToRods(1)

    assert(rods.count === 1)
    assert(rods.filter(_.position.pos == 1L).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).count === 2)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.A).count === 1)
    assert(rods.filter(_.position.pos == 1L).flatMap(_.pileups).filter(_.getReadBase == Base.C).count === 1)
    assert(rods.filter(_.isSingleSample).count === 1)

    val split = rods.adamSplitRodsBySamples()

    assert(split.count === 1)
    assert(split.filter(_.isSingleSample).count === 1)
  }

  sparkTest("check coverage, bases at different pos") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(0L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.C)
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val coverage = pileups.adamPileupsToRods(1)
      .adamRodCoverage()

    // floating point, so apply tolerance
    assert(coverage > 0.99 && coverage < 1.01)
  }

  sparkTest("check coverage, bases at same pos") {
    val p0 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.A)
      .build()
    val p1 = ADAMPileup.newBuilder()
      .setPosition(1L)
      .setReferenceId(0)
      .setReadBase(Base.C)
      .build()

    val pileups: RDD[ADAMPileup] = sc.parallelize(List(p0, p1))

    val coverage = pileups.adamPileupsToRods(1)
      .adamRodCoverage()

    // floating point, so apply tolerance
    assert(coverage > 1.99 && coverage < 2.01)
  }

  sparkTest("sorting reads") {
    val random = new Random("sorting".hashCode)
    val numReadsToCreate = 1000
    val reads = for (i <- 0 until numReadsToCreate) yield {
      val mapped = random.nextBoolean()
      val builder = ADAMRecord.newBuilder().setReadMapped(mapped)
      if (mapped) {
        builder.setReferenceId(random.nextInt(numReadsToCreate / 10)).setStart(random.nextInt(1000000))
      }
      builder.build()
    }
    val rdd = sc.parallelize(reads)
    val sortedReads = rdd.adamSortReadsByReferencePosition().collect().zipWithIndex
    val (mapped, unmapped) = sortedReads.partition(_._1.getReadMapped)
    // Make sure that all the unmapped reads are placed at the end
    assert(unmapped.forall(p => p._2 > mapped.takeRight(1)(0)._2))
    // Make sure that we appropriately sorted the reads
    val expectedSortedReads = mapped.sortWith(
      (a, b) => a._1.getReferenceId < b._1.getReferenceId && a._1.getStart < b._1.getStart)
    assert(expectedSortedReads === mapped)
  }

  sparkTest("convert an RDD of reads into rods") {
    val r0 = ADAMRecord.newBuilder
      .setStart(1L)
      .setReferenceId(0)
      .setSequence("ACG")
      .setMapq(30)
      .setCigar("3M")
      .setMismatchingPositions("3")
      .setReadNegativeStrand(false)
      .setReadMapped(true)
      .setPrimaryAlignment(true)
      .setQual("!#$")
      .build()
    val r1 = ADAMRecord.newBuilder
      .setStart(2L)
      .setReferenceId(0)
      .setSequence("CG")
      .setMapq(40)
      .setCigar("2M")
      .setMismatchingPositions("2")
      .setReadNegativeStrand(false)
      .setReadMapped(true)
      .setPrimaryAlignment(true)
      .setQual("%&")
      .build()
    val r2 = ADAMRecord.newBuilder
      .setStart(3L)
      .setReferenceId(0)
      .setSequence("G")
      .setMapq(50)
      .setCigar("1M")
      .setMismatchingPositions("1")
      .setReadNegativeStrand(false)
      .setReadMapped(true)
      .setPrimaryAlignment(true)
      .setQual("%")
      .build()

    val reads = sc.parallelize(List(r0, r1, r2))

    val rods = reads.adamRecords2Rods()

    assert(rods.count === 3)
    assert(rods.collect.forall(_.position.refId == 0))
    assert(rods.filter(_.position.pos == 1L).count === 1)
    assert(rods.filter(_.position.pos == 1L).first.pileups.length === 1)
    assert(rods.filter(_.position.pos == 1L).first.pileups.forall(_.getReadBase == Base.A))
    assert(rods.filter(_.position.pos == 2L).count === 1)
    assert(rods.filter(_.position.pos == 2L).first.pileups.length === 2)
    assert(rods.filter(_.position.pos == 2L).first.pileups.forall(_.getReadBase == Base.C))
    assert(rods.filter(_.position.pos == 3L).count === 1)
    assert(rods.filter(_.position.pos == 3L).first.pileups.length === 3)
    assert(rods.filter(_.position.pos == 3L).first.pileups.forall(_.getReadBase == Base.G))
  }

  sparkTest("convert an RDD of reads into rods, different references") {
    val r0 = ADAMRecord.newBuilder
      .setStart(1L)
      .setReferenceId(0)
      .setSequence("AC")
      .setMapq(30)
      .setCigar("2M")
      .setMismatchingPositions("2")
      .setReadNegativeStrand(false)
      .setReadMapped(true)
      .setPrimaryAlignment(true)
      .setQual("!#$")
      .build()
    val r1 = ADAMRecord.newBuilder
      .setStart(2L)
      .setReferenceId(0)
      .setSequence("C")
      .setMapq(40)
      .setCigar("1M")
      .setMismatchingPositions("1")
      .setReadNegativeStrand(false)
      .setReadMapped(true)
      .setPrimaryAlignment(true)
      .setQual("%&")
      .build()
    val r2 = ADAMRecord.newBuilder
      .setStart(2L)
      .setReferenceId(1)
      .setSequence("G")
      .setMapq(50)
      .setCigar("1M")
      .setMismatchingPositions("1")
      .setReadNegativeStrand(false)
      .setReadMapped(true)
      .setPrimaryAlignment(true)
      .setQual("%")
      .build()

    val reads = sc.parallelize(List(r0, r1, r2))

    val rods = reads.adamRecords2Rods()

    assert(rods.count === 3)
    assert(rods.filter(_.position.refId == 0).count === 2)
    assert(rods.filter(_.position.refId == 1).count === 1)
    assert(rods.filter(_.position.pos == 1L).filter(_.position.refId == 0).count === 1)
    assert(rods.filter(_.position.pos == 1L).filter(_.position.refId == 0).first.pileups.length === 1)
    assert(rods.filter(_.position.pos == 1L).filter(_.position.refId == 0).first.pileups.forall(_.getReadBase == Base.A))
    assert(rods.filter(_.position.pos == 2L).filter(_.position.refId == 0).count === 1)
    assert(rods.filter(_.position.pos == 2L).filter(_.position.refId == 0).first.pileups.length === 2)
    assert(rods.filter(_.position.pos == 2L).filter(_.position.refId == 0).first.pileups.forall(_.getReadBase == Base.C))
    assert(rods.filter(_.position.pos == 2L).filter(_.position.refId == 1).count === 1)
    assert(rods.filter(_.position.pos == 2L).filter(_.position.refId == 1).first.pileups.length === 1)
    assert(rods.filter(_.position.pos == 2L).filter(_.position.refId == 1).first.pileups.forall(_.getReadBase == Base.G))
  }

  sparkTest("can remap contig ids") {
    val dict = SequenceDictionary(SequenceRecord(0, "chr0", 1000L, "http://bigdatagenomics.github.io/chr0.fa"),
      SequenceRecord(1, "chr1", 1000L, "http://bigdatagenomics.github.io/chr0.fa"))
    val ctg0 = ADAMNucleotideContigFragment.newBuilder()
      .setContigName("chr0")
      .setContigId(1)
      .setContigLength(1000L)
      .build()
    val ctg1 = ADAMNucleotideContigFragment.newBuilder()
      .setContigName("chr1")
      .setContigId(2)
      .setContigLength(1000L)
      .build()

    val rdd = sc.parallelize(List(ctg0, ctg1))

    val remapped = rdd.adamRewriteContigIds(dict)

    assert(remapped.count === 2)
    assert(remapped.filter(_.getContigName.toString == "chr0").first.getContigId === 0)
    assert(remapped.filter(_.getContigName.toString == "chr1").first.getContigId === 1)
  }

  sparkTest("can remap contig ids while filtering out contigs that aren't in dict") {
    val dict = SequenceDictionary(SequenceRecord(0, "chr0", 1000L, "http://bigdatagenomics.github.io/chr0.fa"),
      SequenceRecord(1, "chr1", 1000L, "http://bigdatagenomics.github.io/chr0.fa"))
    val ctg0 = ADAMNucleotideContigFragment.newBuilder()
      .setContigName("chr0")
      .setContigId(1)
      .setContigLength(1000L)
      .build()
    val ctg1 = ADAMNucleotideContigFragment.newBuilder()
      .setContigName("chr2")
      .setContigId(2)
      .setContigLength(1000L)
      .build()

    val rdd = sc.parallelize(List(ctg0, ctg1))

    val remapped = rdd.adamRewriteContigIds(dict)

    assert(remapped.count === 1)
    assert(remapped.filter(_.getContigName.toString == "chr0").first.getContigId === 0)
    assert(remapped.filter(_.getContigName.toString == "chr2").count === 0)
  }

  sparkTest("generate sequence dict from fasta") {
    val ctg0 = ADAMNucleotideContigFragment.newBuilder()
      .setContigName("chr0")
      .setContigId(1)
      .setContigLength(1000L)
      .setUrl("http://bigdatagenomics.github.io/chr0.fa")
      .build()
    val ctg1 = ADAMNucleotideContigFragment.newBuilder()
      .setContigName("chr1")
      .setContigId(2)
      .setContigLength(900L)
      .build()

    val rdd = sc.parallelize(List(ctg0, ctg1))

    val dict = rdd.adamGetSequenceDictionary()

    assert(dict.containsRefName("chr0"))
    assert(dict("chr0").id === 1)
    assert(dict("chr0").length === 1000L)
    assert(dict("chr0").url.toString == "http://bigdatagenomics.github.io/chr0.fa")
    assert(dict.containsRefName("chr1"))
    assert(dict("chr1").id === 2)
    assert(dict("chr1").length === 900L)
  }

  sparkTest("recover samples from variant context") {
    val contig0 = ADAMContig.newBuilder()
      .setContigId(1)
      .setContigName("chr0")
      .build
    val variant0 = ADAMVariant.newBuilder()
      .setPosition(0L)
      .setVariantAllele("A")
      .setReferenceAllele("T")
      .setContig(contig0)
      .build()
    val variant1 = ADAMVariant.newBuilder()
      .setPosition(0L)
      .setVariantAllele("C")
      .setReferenceAllele("T")
      .setContig(contig0)
      .build()
    val genotype0 = ADAMGenotype.newBuilder()
      .setVariant(variant0)
      .setSampleId("me")
      .build()
    val genotype1 = ADAMGenotype.newBuilder()
      .setVariant(variant1)
      .setSampleId("you")
      .build()

    val vc = ADAMVariantContext.buildFromGenotypes(List(genotype0, genotype1))
    val samples = sc.parallelize(List(vc)).adamGetCallsetSamples()

    assert(samples.filter(_ == "you").length === 1)
    assert(samples.filter(_ == "me").length === 1)
  }

  sparkTest("get sequence dictionary from variant context") {
    val contig0 = ADAMContig.newBuilder()
      .setContigName("chr0")
      .setContigId(0)
      .setContigLength(1000)
      .build
    val variant0 = ADAMVariant.newBuilder()
      .setPosition(0L)
      .setVariantAllele("A")
      .setReferenceAllele("T")
      .setContig(contig0)
      .build()
    val variant1 = ADAMVariant.newBuilder()
      .setPosition(0L)
      .setVariantAllele("C")
      .setReferenceAllele("T")
      .setContig(contig0)
      .build()
    val genotype0 = ADAMGenotype.newBuilder()
      .setVariant(variant0)
      .build()
    val genotype1 = ADAMGenotype.newBuilder()
      .setVariant(variant1)
      .build()

    val genotypeSeq = List(genotype0, genotype1)

    val vc = ADAMVariantContext.buildFromGenotypes(genotypeSeq)
    val sequenceDict = sc.parallelize(List(vc)).adamGetSequenceDictionary()

    assert(sequenceDict("chr0").id === 0)
    println(sequenceDict(0).name.getClass)
    assert(sequenceDict(0).name.toString === "chr0")
  }

  sparkTest("characterizeTags counts integer tag values correctly") {
    val tagCounts: Map[String, Long] = Map("XT" -> 10L, "XU" -> 9L, "XV" -> 8L)
    val readItr: Iterable[ADAMRecord] =
      for ((tagName, tagCount) <- tagCounts; i <- 0 until tagCount.toInt)
        yield ADAMRecord.newBuilder().setAttributes("%s:i:%d".format(tagName, i)).build()

    val reads = sc.parallelize(readItr.toSeq)
    val mapCounts: Map[String, Long] = Map(reads.adamCharacterizeTags().collect(): _*)

    assert(mapCounts === tagCounts)
  }

  sparkTest("withTag returns only those records which have the appropriate tag") {
    val r1 = ADAMRecord.newBuilder().setAttributes("XX:i:3").build()
    val r2 = ADAMRecord.newBuilder().setAttributes("XX:i:4\tYY:i:10").build()
    val r3 = ADAMRecord.newBuilder().setAttributes("YY:i:20").build()

    val rdd = sc.parallelize(Seq(r1, r2, r3))
    assert(rdd.count() === 3)

    val rddXX = rdd.adamFilterRecordsWithTag("XX")
    assert(rddXX.count() === 2)

    val collected = rddXX.collect()
    assert(collected.contains(r1))
    assert(collected.contains(r2))
  }

  sparkTest("withTag, when given a tag name that doesn't exist in the input, returns an empty RDD") {
    val r1 = ADAMRecord.newBuilder().setAttributes("XX:i:3").build()
    val r2 = ADAMRecord.newBuilder().setAttributes("XX:i:4\tYY:i:10").build()
    val r3 = ADAMRecord.newBuilder().setAttributes("YY:i:20").build()

    val rdd = sc.parallelize(Seq(r1, r2, r3))
    assert(rdd.count() === 3)

    val rddXX = rdd.adamFilterRecordsWithTag("ZZ")
    assert(rddXX.count() === 0)
  }

  sparkTest("characterizeTagValues counts distinct values of a tag") {
    val r1 = ADAMRecord.newBuilder().setAttributes("XX:i:3").build()
    val r2 = ADAMRecord.newBuilder().setAttributes("XX:i:4\tYY:i:10").build()
    val r3 = ADAMRecord.newBuilder().setAttributes("YY:i:20").build()
    val r4 = ADAMRecord.newBuilder().setAttributes("XX:i:4").build()

    val rdd = sc.parallelize(Seq(r1, r2, r3, r4))
    val tagValues = rdd.adamCharacterizeTagValues("XX")

    assert(tagValues.keys.size === 2)
    assert(tagValues(4) === 2)
    assert(tagValues(3) === 1)
  }

  sparkTest("characterizeTags counts tags in a SAM file correctly") {
    val filePath = getClass.getClassLoader.getResource("reads12.sam").getFile
    val sam: RDD[ADAMRecord] = sc.adamLoad(filePath)

    val mapCounts: Map[String, Long] = Map(sam.adamCharacterizeTags().collect(): _*)
    assert(mapCounts("NM") === 200)
    assert(mapCounts("AS") === 200)
    assert(mapCounts("XS") === 200)
  }

  sparkTest("recover reference string from a single contig fragment") {
    val sequence = "ACTGTAC"
    val fragment = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(1)
      .setContigName("chr1")
      .setFragmentSequence(sequence)
      .setContigLength(7L)
      .setFragmentNumber(0)
      .setFragmentStartPosition(0L)
      .setNumberOfFragmentsInContig(1)
      .build()
    val region = ReferenceRegion(fragment).get

    val rdd = sc.parallelize(List(fragment))

    assert(rdd.adamGetReferenceString(region) === "ACTGTAC")
  }

  sparkTest("recover trimmed reference string from a single contig fragment") {
    val sequence = "ACTGTAC"
    val fragment = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(1)
      .setContigName("chr1")
      .setFragmentSequence(sequence)
      .setContigLength(7L)
      .setFragmentNumber(0)
      .setFragmentStartPosition(0L)
      .setNumberOfFragmentsInContig(1)
      .build()
    val region = new ReferenceRegion(1, 1L, 6L)

    val rdd = sc.parallelize(List(fragment))

    assert(rdd.adamGetReferenceString(region) === "CTGTA")
  }

  sparkTest("recover reference string from multiple contig fragments") {
    val sequence = "ACTGTACTC"
    val sequence0 = sequence.take(7) // ACTGTAC
    val sequence1 = sequence.drop(3).take(5) // GTACT 
    val sequence2 = sequence.takeRight(6).reverse // CTCATG
    val fragment0 = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(1)
      .setContigName("chr1")
      .setFragmentSequence(sequence0)
      .setContigLength(7L)
      .setFragmentNumber(0)
      .setFragmentStartPosition(0L)
      .setNumberOfFragmentsInContig(1)
      .build()
    val fragment1 = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(2)
      .setContigName("chr2")
      .setFragmentSequence(sequence1)
      .setContigLength(11L)
      .setFragmentNumber(0)
      .setFragmentStartPosition(0L)
      .setNumberOfFragmentsInContig(2)
      .build()
    val fragment2 = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(2)
      .setContigName("chr2")
      .setFragmentSequence(sequence2)
      .setContigLength(11L)
      .setFragmentNumber(1)
      .setFragmentStartPosition(5L)
      .setNumberOfFragmentsInContig(2)
      .build()
    val region0 = ReferenceRegion(fragment0).get
    val region1 = ReferenceRegion(fragment1).get.merge(ReferenceRegion(fragment2).get)

    val rdd = sc.parallelize(List(fragment0, fragment1, fragment2))

    assert(rdd.adamGetReferenceString(region0) === "ACTGTAC")
    assert(rdd.adamGetReferenceString(region1) === "GTACTCTCATG")
  }

  sparkTest("recover trimmed reference string from multiple contig fragments") {
    val sequence = "ACTGTACTC"
    val sequence0 = sequence.take(7) // ACTGTAC
    val sequence1 = sequence.drop(3).take(5) // GTACT 
    val sequence2 = sequence.takeRight(6).reverse // CTCATG
    val fragment0 = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(1)
      .setContigName("chr1")
      .setFragmentSequence(sequence0)
      .setContigLength(7L)
      .setFragmentNumber(0)
      .setFragmentStartPosition(0L)
      .setNumberOfFragmentsInContig(1)
      .build()
    val fragment1 = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(2)
      .setContigName("chr2")
      .setFragmentSequence(sequence1)
      .setContigLength(11L)
      .setFragmentNumber(0)
      .setFragmentStartPosition(0L)
      .setNumberOfFragmentsInContig(2)
      .build()
    val fragment2 = ADAMNucleotideContigFragment.newBuilder()
      .setContigId(2)
      .setContigName("chr2")
      .setFragmentSequence(sequence2)
      .setContigLength(11L)
      .setFragmentNumber(1)
      .setFragmentStartPosition(5L)
      .setNumberOfFragmentsInContig(2)
      .build()
    val region0 = new ReferenceRegion(1, 1L, 6L)
    val region1 = new ReferenceRegion(2, 3L, 9L)

    val rdd = sc.parallelize(List(fragment0, fragment1, fragment2))

    assert(rdd.adamGetReferenceString(region0) === "CTGTA")
    assert(rdd.adamGetReferenceString(region1) === "CTCTCA")
  }

}
